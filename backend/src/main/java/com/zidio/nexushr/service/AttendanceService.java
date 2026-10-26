package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PayrollRepository;
import com.zidio.nexushr.web.dto.AttendanceImportResponse;
import com.zidio.nexushr.web.dto.AttendanceImportRowError;
import com.zidio.nexushr.web.dto.AttendanceMonthlyEmployeeSummary;
import com.zidio.nexushr.web.dto.AttendanceMetricsResponse;
import com.zidio.nexushr.web.dto.AttendanceMonthlySummaryResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.zidio.nexushr.domain.PayrollStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    public static final ZoneId COMPANY_ZONE = ZoneId.of("Asia/Kolkata");

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    /** Salary-workbook sheet names that indicate a payroll Excel, not a plain attendance file. */
    private static final List<String> SALARY_SHEET_SIGNALS = List.of(
            "employs salaris", "cyond ndt", "g group structural", "tech 9",
            "gorle group", "salary"
    );

    private static final int BATCH_SIZE = 50;

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollRepository payrollRepository;
    private final ExcelPayrollService excelPayrollService;

    @Value("${app.attendance.standard-daily-minutes:540}")
    private int standardDailyMinutes = 540;

    @Value("${app.attendance.workday-start:09:30}")
    private String workdayStart = "09:30";

    @Autowired(required = false)
    private AttendancePolicyService attendancePolicyService;

    @Autowired(required = false)
    private CompanyHolidayCalendar companyHolidayCalendar;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            EmployeeRepository employeeRepository,
            PayrollRepository payrollRepository,
            @Lazy ExcelPayrollService excelPayrollService) {

        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.payrollRepository = payrollRepository;
        this.excelPayrollService = excelPayrollService;
    }

    public AttendanceRecord create(AttendanceRecord attendanceRecord) {

        if (attendanceRecord == null) {
            throw badRequest("Attendance record is required");
        }

        if (attendanceRecord.getEmployee() == null
                || attendanceRecord.getEmployee().getId() == null) {
            throw badRequest("Employee ID is required");
        }

        if (attendanceRecord.getAttendanceDate() == null) {
            throw badRequest("Attendance date is required");
        }

        Long employeeId = attendanceRecord.getEmployee().getId();
        LocalDate date = attendanceRecord.getAttendanceDate();

        Employee employee = getActiveEmployee(employeeId);
        attendanceRecord.setEmployee(employee);

        if (attendanceRepository.existsByEmployee_IdAndAttendanceDate(
                employeeId, date)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Attendance already exists for this employee and date"
            );
        }

        LocalDateTime checkIn = attendanceRecord.getCheckInTime();
        LocalDateTime checkOut = attendanceRecord.getCheckOutTime();

        if (checkIn == null) {
            throw badRequest("Check-in time is required");
        }

        validateDateMatchesAttendance(
                date,
                checkIn,
                "Check-in time must match attendance date"
        );

        if (checkOut != null) {
            validateDateMatchesAttendance(
                    date,
                    checkOut,
                    "Check-out time must match attendance date"
            );

            validateCheckTimes(checkIn, checkOut);

            int workMinutes = AttendanceRules.workedMinutes(checkIn, checkOut);

            attendanceRecord.setWorkMinutes(workMinutes);
            attendanceRecord.setOvertimeMinutes(
                    calculateOvertimeMinutes(workMinutes)
            );
        } else {
            attendanceRecord.setWorkMinutes(0);
            attendanceRecord.setOvertimeMinutes(0);
        }

        int lateMinutes = calculateLateArrivalMinutes(checkIn);
        attendanceRecord.setLateArrivalMinutes(lateMinutes);
        attendanceRecord.setLateWarning(lateMinutes > 0);

        return attendanceRepository.save(attendanceRecord);
    }

    public AttendanceRecord checkIn(Long employeeId) {
        return checkIn(employeeId, employeeId, false);
    }

    public AttendanceRecord checkIn(Long employeeId, Long actorId, boolean selfieAttached) {

        Employee employee = getActiveEmployee(employeeId);

        LocalDate today = LocalDate.now(COMPANY_ZONE);
        LocalDateTime now = LocalDateTime.now(COMPANY_ZONE);

        AttendanceRecord record = attendanceRepository
                .findByEmployee_IdAndAttendanceDate(employeeId, today)
                .orElseGet(AttendanceRecord::new);

        if (selfieAttached && !employee.isSiteVisitor()) {
            throw badRequest("Selfie check-in is only for site staff");
        }
        if (employee.isSiteVisitor() && !selfieAttached) {
            throw badRequest("Site visitors must check in with a selfie photo");
        }

        restoreClockedWork(record);
        AttendanceDay day = dayFrom(record);
        try {
            day.checkIn(now);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Already checked in. Check out before checking in again."
            );
        }

        record.setEmployee(employee);
        record.setAttendanceDate(today);
        applyDay(record, day);
        record.setLateArrivalMinutes(calculateLateArrivalMinutes(record.getCheckInTime()));
        record.setOvertimeMinutes(calculateOvertimeMinutes(day.closedWorkMinutes()));
        if (selfieAttached) {
            record.setSelfieCaptured(true);
        }
        if (attendancePolicyService != null) {
            attendancePolicyService.onCheckIn(record, actor(actorId, employee), selfieAttached);
        }

        return attendanceRepository.save(record);
    }

    public AttendanceRecord checkOut(Long employeeId) {
        return checkOut(employeeId, employeeId);
    }

    public AttendanceRecord checkOut(Long employeeId, Long actorId) {
        return checkOut(employeeId, actorId, false);
    }

    public AttendanceRecord checkOut(Long employeeId, Long actorId, boolean selfieAttached) {

        Employee employee = getActiveEmployee(employeeId);
        if (employee.isSiteVisitor() && !selfieAttached) {
            throw badRequest("Site visitors must check out with a selfie photo");
        }

        LocalDate today = LocalDate.now(COMPANY_ZONE);

        AttendanceRecord record =
                attendanceRepository
                        .findByEmployee_IdAndAttendanceDate(
                                employeeId,
                                today
                        )
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "No check-in found for employee today"
                        ));

        LocalDateTime now = LocalDateTime.now(COMPANY_ZONE);
        restoreClockedWork(record);
        AttendanceDay day = dayFrom(record);
        try {
            day.checkOut(now);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage()
            );
        }

        applyDay(record, day);
        if (attendancePolicyService != null) {
            attendancePolicyService.onCheckOut(record, actor(actorId, employee));
        }
        record.setOvertimeMinutes(calculateOvertimeMinutes(
                record.getClosedWorkMinutes() == null ? 0 : record.getClosedWorkMinutes()));

        if (record.getLateArrivalMinutes() == null) {
            record.setLateArrivalMinutes(
                    calculateLateArrivalMinutes(record.getCheckInTime())
            );
        }

        return attendanceRepository.save(record);
    }

    private Employee actor(Long actorId, Employee fallback) {
        if (actorId == null || fallback.getId() != null && actorId.equals(fallback.getId())) {
            return fallback;
        }
        return employeeRepository.findById(actorId).orElse(fallback);
    }

    public java.util.Map<String, Object> myStatus(Long employeeId) {
        LocalDate today = LocalDate.now(COMPANY_ZONE);
        AttendanceRecord record = attendanceRepository
                .findByEmployee_IdAndAttendanceDate(employeeId, today)
                .orElse(null);
        String status = record == null || record.getAttendanceStatus() == null
                ? "NONE"
                : record.getAttendanceStatus();
        boolean open = record != null
                && record.getCheckInTime() != null
                && record.getCheckOutTime() == null
                && !"INACTIVE".equals(status);
        String message = switch (status) {
            case "OPEN" -> "Checked in";
            case "CLOSED" -> "Checked out";
            case "INACTIVE" -> "Marked inactive. Check in or check out is missing.";
            default -> open ? "Checked in" : "Check in is missing";
        };
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", open ? "OPEN" : status);
        body.put("checkedIn", record != null && record.getCheckInTime() != null);
        body.put("checkedOut", record != null && record.getCheckOutTime() != null);
        body.put("message", message);
        body.put("blueFlag", record != null && Boolean.TRUE.equals(record.getBlueFlag()));
        body.put("yellowFlag", record != null && Boolean.TRUE.equals(record.getYellowFlag()));
        body.put("redFlag", record != null && Boolean.TRUE.equals(record.getRedFlag()));
        body.put("lateWarning", record != null && Boolean.TRUE.equals(record.getLateWarning()));
        return body;
    }

    public AttendanceRecord heartbeat(Long employeeId) {
        if (attendancePolicyService == null) {
            return null;
        }
        return attendancePolicyService.heartbeat(employeeId);
    }

    @Transactional
    public AttendanceRecord siteCheckIn(Long employeeId, String contentType, byte[] bytes) {
        AttendanceRecord record = checkIn(employeeId, employeeId, true);
        saveSelfie(employeeId, LocalDate.now(COMPANY_ZONE), contentType, bytes, "CHECK_IN");
        record.setSelfieCaptured(true);
        return attendanceRepository.save(record);
    }

    @Transactional
    public AttendanceRecord siteCheckOut(Long employeeId, String contentType, byte[] bytes) {
        AttendanceRecord record = checkOut(employeeId, employeeId, true);
        saveSelfie(employeeId, LocalDate.now(COMPANY_ZONE), contentType, bytes, "CHECK_OUT");
        record.setSelfieCaptured(true);
        return attendanceRepository.save(record);
    }

    public void saveSelfie(Long employeeId, LocalDate date, String contentType, byte[] bytes) {
        saveSelfie(employeeId, date, contentType, bytes, "CHECK_IN");
    }

    public void saveSelfie(Long employeeId, LocalDate date, String contentType, byte[] bytes, String punchType) {
        if (attendancePolicyService == null) {
            return;
        }
        AttendanceRecord saved = attendancePolicyService.saveSelfie(employeeId, date, contentType, bytes, punchType);
        if (saved != null) {
            saved.setSelfieCaptured(true);
            attendanceRepository.save(saved);
        }
    }

    public java.util.List<com.zidio.nexushr.domain.AttendanceAuditLog> auditTrail(Long employeeId) {
        if (attendancePolicyService == null) {
            return java.util.List.of();
        }
        return attendancePolicyService.auditTrail(employeeId);
    }

    /**
     * Work for the day is time checked in, minus one flexible lunch hour.
     * A single check-in and check-out uses the full span. Idle time does not replace it.
     */
    static int clockedMinutes(AttendanceRecord record) {
        if (record == null) {
            return 0;
        }
        int breakMinutes = record.getBreakMinutes() == null ? 0 : record.getBreakMinutes();
        int closed = record.getClosedWorkMinutes() == null ? 0 : record.getClosedWorkMinutes();
        if (breakMinutes == 0
                && record.getCheckInTime() != null
                && record.getCheckOutTime() != null) {
            closed = Math.max(closed, AttendanceRules.sessionMinutes(
                    record.getCheckInTime(), record.getCheckOutTime()));
        }
        if (closed == 0) {
            return record.getWorkMinutes() == null ? 0 : record.getWorkMinutes();
        }
        return AttendanceRules.payableWorkMinutes(closed, breakMinutes);
    }

    private void restoreClockedWork(AttendanceRecord record) {
        if (record == null || record.getCheckInTime() == null || record.getCheckOutTime() == null) {
            return;
        }
        int breakMinutes = record.getBreakMinutes() == null ? 0 : record.getBreakMinutes();
        int closed = record.getClosedWorkMinutes() == null ? 0 : record.getClosedWorkMinutes();
        if (breakMinutes == 0) {
            closed = Math.max(closed, AttendanceRules.sessionMinutes(
                    record.getCheckInTime(), record.getCheckOutTime()));
        }
        int payable = AttendanceRules.payableWorkMinutes(closed, breakMinutes);
        int stored = record.getWorkMinutes() == null ? 0 : record.getWorkMinutes();
        int storedClosed = record.getClosedWorkMinutes() == null ? 0 : record.getClosedWorkMinutes();
        if (stored == payable && storedClosed == closed) {
            return;
        }
        record.setClosedWorkMinutes(closed);
        record.setWorkMinutes(payable);
        record.setOvertimeMinutes(calculateOvertimeMinutes(payable));
    }

    private void restoreListed(List<AttendanceRecord> records) {
        if (records == null) {
            return;
        }
        for (AttendanceRecord record : records) {
            int before = record.getWorkMinutes() == null ? 0 : record.getWorkMinutes();
            restoreClockedWork(record);
            int after = record.getWorkMinutes() == null ? 0 : record.getWorkMinutes();
            if (after != before) {
                attendanceRepository.save(record);
            }
        }
    }

    private AttendanceDay dayFrom(AttendanceRecord record) {
        AttendanceDay day = new AttendanceDay();
        int closed = record.getClosedWorkMinutes() == null ? 0 : record.getClosedWorkMinutes();
        if (closed == 0
                && record.getCheckOutTime() != null
                && record.getWorkMinutes() != null) {
            closed = record.getWorkMinutes();
        }
        day.restore(
                record.getCheckInTime(),
                record.getCheckOutTime(),
                record.getSessionStartedAt(),
                closed,
                record.getBreakMinutes() == null ? 0 : record.getBreakMinutes(),
                record.getAttendanceStatus());
        return day;
    }

    private void applyDay(AttendanceRecord record, AttendanceDay day) {
        record.setCheckInTime(day.firstCheckIn());
        record.setCheckOutTime(day.lastCheckOut());
        record.setSessionStartedAt(day.sessionStartedAt());
        record.setClosedWorkMinutes(day.closedWorkMinutes());
        record.setBreakMinutes(day.breakMinutes());
        record.setWorkMinutes(AttendanceRules.payableWorkMinutes(
                day.closedWorkMinutes(), day.breakMinutes()));
        record.setAttendanceStatus(day.status());
    }

    public AttendanceRecord biometricCheckIn(Long employeeId) {
        return checkIn(employeeId);
    }

    public AttendanceRecord biometricCheckOut(Long employeeId) {
        return checkOut(employeeId);
    }

    public List<AttendanceRecord> listByDate(LocalDate date) {

        if (date == null) {
            throw badRequest("Date is required");
        }

        List<AttendanceRecord> records = attendanceRepository.findByAttendanceDate(date);
        restoreListed(records);
        return records;
    }

    public List<AttendanceRecord> listByEmployee(Long employeeId) {

        getActiveEmployee(employeeId);

        List<AttendanceRecord> records = attendanceRepository
                .findByEmployee_IdOrderByAttendanceDateDesc(employeeId);
        restoreListed(records);
        return records;
    }

    public AttendanceMetricsResponse getMetrics(LocalDate date) {

        if (date == null) {
            throw badRequest("Date is required");
        }

        List<AttendanceRecord> records =
                attendanceRepository.findByAttendanceDate(date);

        long present = records.size();

        long checkedOut = records.stream()
                .filter(record -> record.getCheckOutTime() != null)
                .count();

        long late = records.stream()
                .filter(record ->
                        record.getLateArrivalMinutes() != null
                                && record.getLateArrivalMinutes() > 0)
                .count();

        long overtime = records.stream()
                .filter(record ->
                        record.getOvertimeMinutes() != null
                                && record.getOvertimeMinutes() > 0)
                .count();

        long workMinutes = records.stream()
                .map(AttendanceRecord::getWorkMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        long overtimeMinutes = records.stream()
                .map(AttendanceRecord::getOvertimeMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        return new AttendanceMetricsResponse(
                date,
                employeeRepository.count(),
                present,
                checkedOut,
                late,
                overtime,
                workMinutes,
                overtimeMinutes
        );
    }

    @Transactional
    public AttendanceImportResponse importMonthlyExcel(
            MultipartFile file,
            String payMonth) {

        if (file == null || file.isEmpty()) {
            throw badRequest("Excel file is required");
        }

        YearMonth month = parsePayMonth(payMonth);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("nexushr_att_", ".xlsx");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (isSalaryWorkbook(tempPath)) {
                log.info("[AttendanceService] Salary workbook detected – delegating to ExcelPayrollService for payMonth={}", payMonth);
                List<PayrollRecord> payrollRecords = excelPayrollService.importPayroll(tempPath, payMonth);
                int count = payrollRecords.size();
                return new AttendanceImportResponse(
                        payMonth, count, count, 0, 0, List.of()
                );
            }

            // A full workbook load is what previously exhausted the Render heap and
            // took the API down for every user. Large files stay on the streaming path.
            if (Files.size(tempPath) > 1_048_576L) {
                throw badRequest(
                        "This Excel file is too large for the attendance importer. Upload it with Import Salary Excel."
                );
            }

            try (Workbook workbook = WorkbookFactory.create(tempPath.toFile())) {

                // ── Standard attendance format ──────────────────────────────────────
                Sheet sheet = workbook.getNumberOfSheets() > 0
                        ? workbook.getSheetAt(0)
                        : null;

                if (sheet == null) {
                    throw badRequest("Excel sheet is missing");
                }

                // Pre-load all active employees once to eliminate N+1 remote database roundtrips
                List<Employee> activeEmployees = employeeRepository.findByActiveTrueAndLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);
                Map<String, Employee> employeeByCode = new HashMap<>();
                for (Employee emp : activeEmployees) {
                    if (emp.getEmployeeCode() != null) {
                        employeeByCode.put(emp.getEmployeeCode().trim().toUpperCase(Locale.ROOT), emp);
                    }
                }

                // Pre-load all attendance records for this month once
                List<AttendanceRecord> monthRecords = attendanceRepository.findByAttendanceDateBetween(monthStart, monthEnd);
                Map<String, AttendanceRecord> recordMap = new HashMap<>();
                for (AttendanceRecord r : monthRecords) {
                    if (r.getEmployee() != null && r.getEmployee().getId() != null && r.getAttendanceDate() != null) {
                        recordMap.put(r.getEmployee().getId() + "_" + r.getAttendanceDate(), r);
                    }
                }

                int totalRows = 0;
                int importedCount = 0;
                int updatedCount = 0;
                int skippedCount = 0;

                List<AttendanceRecord> recordsToSave = new ArrayList<>();
                Set<Employee> affectedEmployees = new HashSet<>();
                List<AttendanceImportRowError> errors = new ArrayList<>();
                DataFormatter formatter = new DataFormatter(Locale.ENGLISH);

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);

                    if (row == null || isBlankRow(row, formatter)) {
                        skippedCount++;
                        continue;
                    }

                    totalRows++;

                    try {
                        String employeeCode = formatter
                                .formatCellValue(row.getCell(0))
                                .trim();

                        if (employeeCode.isBlank()) {
                            throw badRequest("Employee code is required in column A");
                        }

                        LocalDate attendanceDate = parseAttendanceDate(
                                row.getCell(1),
                                month,
                                i + 1
                        );

                        if (attendanceDate.isBefore(monthStart)
                                || attendanceDate.isAfter(monthEnd)) {
                            throw badRequest("Attendance date is outside pay month " + payMonth);
                        }

                        LocalTime checkInTime = parseOptionalTime(row.getCell(2), formatter);
                        LocalTime checkOutTime = parseOptionalTime(row.getCell(3), formatter);

                        if (checkInTime == null && checkOutTime == null) {
                            skippedCount++;
                            continue;
                        }

                        if (checkInTime == null) {
                            throw badRequest("Check-in time is required when check-out is present");
                        }

                        LocalDateTime checkIn = attendanceDate.atTime(checkInTime);
                        LocalDateTime checkOut = checkOutTime == null
                                ? null
                                : attendanceDate.atTime(checkOutTime);

                        if (checkOut != null) {
                            validateCheckTimes(checkIn, checkOut);
                        }

                        Employee employee = employeeByCode.get(employeeCode.toUpperCase(Locale.ROOT));
                        if (employee == null) {
                            employee = employeeRepository
                                    .findByEmployeeCodeIgnoreCase(employeeCode)
                                    .orElseThrow(() -> new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Employee not found for code: " + employeeCode
                                    ));

                            if (!employee.isActive()
                                    || employee.getLifecycleStatus() != EmployeeLifecycleStatus.ACTIVE) {
                                throw badRequest("Employee is not active: " + employeeCode);
                            }
                            employeeByCode.put(employeeCode.toUpperCase(Locale.ROOT), employee);
                        }

                        String key = employee.getId() + "_" + attendanceDate;
                        AttendanceRecord record = recordMap.get(key);
                        boolean exists = record != null;

                        if (!exists) {
                            record = new AttendanceRecord();
                            recordMap.put(key, record);
                        }

                        record.setEmployee(employee);
                        record.setAttendanceDate(attendanceDate);
                        record.setCheckInTime(checkIn);
                        record.setCheckOutTime(checkOut);

                        int workMinutes = checkOut == null
                                ? 0
                                : AttendanceRules.workedMinutes(checkIn, checkOut);

                        record.setWorkMinutes(workMinutes);
                        record.setClosedWorkMinutes(checkOut == null
                                ? 0
                                : AttendanceRules.sessionMinutes(checkIn, checkOut));
                        record.setBreakMinutes(0);
                        record.setAttendanceStatus(checkOut == null ? "OPEN" : "CLOSED");
                        int lateMinutes = calculateLateArrivalMinutes(checkIn);
                        record.setLateArrivalMinutes(lateMinutes);
                        record.setLateWarning(lateMinutes > 0);
                        record.setOvertimeMinutes(calculateOvertimeMinutes(workMinutes));

                        recordsToSave.add(record);
                        affectedEmployees.add(employee);

                        if (exists) {
                            updatedCount++;
                        } else {
                            importedCount++;
                        }

                        // Flush in manageable batches to minimize memory and transaction overhead
                        if (recordsToSave.size() >= BATCH_SIZE) {
                            attendanceRepository.saveAll(recordsToSave);
                            recordsToSave.clear();
                        }

                    } catch (Exception ex) {
                        String employeeCode = formatter
                                .formatCellValue(row.getCell(0))
                                .trim();

                        errors.add(new AttendanceImportRowError(
                                i + 1,
                                employeeCode,
                                simplifyMessage(ex)
                        ));
                    }
                }

                // Flush remaining attendance records
                if (!recordsToSave.isEmpty()) {
                    attendanceRepository.saveAll(recordsToSave);
                    recordsToSave.clear();
                }

                // Calculate salary based on the imported attendance for all affected employees
                if (!affectedEmployees.isEmpty()) {
                    calculateAndSavePayrollForAttendance(affectedEmployees, month, payMonth);
                }

                return new AttendanceImportResponse(
                        payMonth,
                        totalRows,
                        importedCount,
                        updatedCount,
                        skippedCount,
                        errors
                );
            }

        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ResponseStatusException) {
                throw (ResponseStatusException) ex;
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to read Excel file. Please upload a valid .xlsx or .xls sheet.",
                    ex
            );
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Transactional
    public AttendanceImportResponse importPastedAttendance(String payMonth, String text) {
        if (payMonth == null || payMonth.isBlank()) {
            throw badRequest("Pay month is required");
        }
        if (text == null || text.isBlank()) {
            throw badRequest("Paste the Excel rows first");
        }
        YearMonth month = YearMonth.parse(payMonth.trim());
        String[] lines = text.split("\\R");
        int importedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        List<AttendanceImportRowError> errors = new ArrayList<>();
        Set<Employee> affected = new HashSet<>();
        int rowNumber = 0;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            rowNumber++;
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("employee") && (lower.contains("check") || lower.contains("code"))) {
                skippedCount++;
                continue;
            }
            String[] cols = line.split("\\t|\\s*,\\s*");
            try {
                ParsedPasteRow parsed = parsePasteRow(cols, month);
                Employee employee = employeeRepository
                        .findByEmployeeCodeIgnoreCase(parsed.code())
                        .orElseThrow(() -> badRequest("Employee not found: " + parsed.code()));
                AttendanceRecord record = attendanceRepository
                        .findByEmployee_IdAndAttendanceDate(employee.getId(), parsed.date())
                        .orElseGet(AttendanceRecord::new);
                boolean exists = record.getId() != null;
                record.setEmployee(employee);
                record.setAttendanceDate(parsed.date());
                record.setCheckInTime(parsed.checkIn());
                record.setCheckOutTime(parsed.checkOut());
                int workMinutes = AttendanceRules.workedMinutes(parsed.checkIn(), parsed.checkOut());
                record.setWorkMinutes(workMinutes);
                record.setClosedWorkMinutes(AttendanceRules.sessionMinutes(parsed.checkIn(), parsed.checkOut()));
                record.setBreakMinutes(0);
                int lateMinutes = calculateLateArrivalMinutes(parsed.checkIn());
                record.setLateArrivalMinutes(lateMinutes);
                record.setLateWarning(lateMinutes > 0);
                record.setAttendanceStatus("CLOSED");
                record.setOvertimeMinutes(calculateOvertimeMinutes(workMinutes));
                attendanceRepository.save(record);
                affected.add(employee);
                if (exists) {
                    updatedCount++;
                } else {
                    importedCount++;
                }
            } catch (Exception ex) {
                errors.add(new AttendanceImportRowError(rowNumber, cols.length > 0 ? cols[0] : "", simplifyMessage(ex)));
            }
        }

        if (!affected.isEmpty()) {
            calculateAndSavePayrollForAttendance(affected, month, payMonth.trim());
        }

        return new AttendanceImportResponse(
                payMonth.trim(),
                rowNumber,
                importedCount,
                updatedCount,
                skippedCount,
                errors
        );
    }

    private ParsedPasteRow parsePasteRow(String[] cols, YearMonth month) {
        List<String> values = new ArrayList<>();
        for (String col : cols) {
            if (col != null && !col.isBlank()) {
                values.add(col.trim());
            }
        }
        if (values.size() < 3) {
            throw badRequest("Use columns: date, employee code, check in, check out");
        }
        LocalDate date;
        String code;
        String inText;
        String outText;
        if (values.size() >= 4 && looksLikeDate(values.get(0))) {
            date = parsePasteDate(values.get(0), month);
            code = values.get(1);
            inText = values.get(2);
            outText = values.get(3);
        } else {
            date = month.atDay(1);
            code = values.get(0);
            inText = values.get(1);
            outText = values.get(2);
        }
        LocalTime in = LocalTime.parse(normalizeClock(inText));
        LocalTime out = LocalTime.parse(normalizeClock(outText));
        return new ParsedPasteRow(code, date.atTime(in), date.atTime(out), date);
    }

    private static boolean looksLikeDate(String value) {
        return value.chars().anyMatch(ch -> ch == '-' || ch == '/');
    }

    private static LocalDate parsePasteDate(String value, YearMonth month) {
        String text = value.trim();
        try {
            if (text.length() <= 5) {
                String[] parts = text.split("[/-]");
                int day = Integer.parseInt(parts[0]);
                int mon = parts.length > 1 ? Integer.parseInt(parts[1]) : month.getMonthValue();
                return LocalDate.of(month.getYear(), mon, day);
            }
            return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read date: " + value);
        }
    }

    private static String normalizeClock(String value) {
        String text = value.trim();
        if (text.length() == 4 && text.charAt(1) == ':') {
            text = "0" + text;
        }
        if (text.length() == 5) {
            return text + ":00";
        }
        return text;
    }

    private record ParsedPasteRow(String code, LocalDateTime checkIn, LocalDateTime checkOut, LocalDate date) {
    }

    /**
     * Calculate and update salary records for employees based on attendance worked days and hours.
     */
    private void calculateAndSavePayrollForAttendance(
            Set<Employee> employees,
            YearMonth month,
            String payMonth) {

        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        List<com.zidio.nexushr.domain.CompanyHoliday> holidayRows = companyHolidayCalendar == null
                ? List.of()
                : companyHolidayCalendar.between(startDate, endDate);
        List<AttendanceRecord> allMonthAttendance = attendanceRepository.findByAttendanceDateBetween(startDate, endDate);
        Map<Long, List<AttendanceRecord>> attendanceByEmployee = allMonthAttendance.stream()
                .filter(r -> r.getEmployee() != null && r.getEmployee().getId() != null)
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));

        List<PayrollRecord> existingPayrolls = payrollRepository.findByPayMonth(payMonth);
        Map<Long, PayrollRecord> existingPayrollMap = new HashMap<>();
        for (PayrollRecord pr : existingPayrolls) {
            if (pr.getEmployee() != null && pr.getEmployee().getId() != null) {
                existingPayrollMap.put(pr.getEmployee().getId(), pr);
            }
        }

        List<PayrollRecord> payrollToSave = new ArrayList<>();

        for (Employee employee : employees) {
            LocalDate effectiveStart = employee.getJoiningDate() != null
                    && employee.getJoiningDate().isAfter(startDate)
                    ? employee.getJoiningDate()
                    : startDate;

            String companyCode = employee.getCompany() == null ? null : employee.getCompany().getCode();
            boolean gorle = HoursPayPolicy.gorle(companyCode);
            Set<LocalDate> holidays = companyHolidayCalendar == null
                    ? Set.of()
                    : companyHolidayCalendar.forCompany(holidayRows, companyCode);
            int expectedWorkMinutes = HoursPayPolicy.expectedPaidMinutes(
                    effectiveStart, endDate, gorle, holidays);

            List<AttendanceRecord> records = attendanceByEmployee.getOrDefault(employee.getId(), List.of());

            int workedMinutes = records.stream()
                    .map(AttendanceRecord::getWorkMinutes)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
            int deductedMinutes = records.stream()
                    .map(AttendanceRecord::getDeductedMinutes)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
            workedMinutes = Math.max(0, workedMinutes - deductedMinutes);

            int overtimeMinutes = records.stream()
                    .map(AttendanceRecord::getOvertimeMinutes)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

            int shortfallMinutes = HoursPayPolicy.deductibleShortfall(expectedWorkMinutes, workedMinutes);

            BigDecimal baseSalary = employee.getBaseSalary() != null && employee.getBaseSalary().compareTo(BigDecimal.ZERO) > 0
                    ? employee.getBaseSalary()
                    : BigDecimal.valueOf(30000);

            BigDecimal expectedMinutesVal = BigDecimal.valueOf(Math.max(1, expectedWorkMinutes));
            int payableMinutes = Math.max(0, expectedWorkMinutes - shortfallMinutes);
            BigDecimal earnedSalary = expectedWorkMinutes <= 0
                    ? baseSalary
                    : baseSalary
                    .multiply(BigDecimal.valueOf(payableMinutes))
                    .divide(expectedMinutesVal, 2, RoundingMode.HALF_UP);
            BigDecimal perMinuteRate = baseSalary.divide(expectedMinutesVal, 8, RoundingMode.HALF_UP);

            BigDecimal leaveDeduction = baseSalary.subtract(earnedSalary).max(BigDecimal.ZERO);

            BigDecimal overtimeAmount = perMinuteRate
                    .multiply(BigDecimal.valueOf(overtimeMinutes))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal basicSalary = earnedSalary.multiply(BigDecimal.valueOf(0.60)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal hra = earnedSalary.multiply(BigDecimal.valueOf(0.40)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal grossSalary = earnedSalary.add(overtimeAmount);

            BigDecimal pf = basicSalary.multiply(BigDecimal.valueOf(0.12)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = grossSalary.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal deductions = pf.add(taxAmount);
            BigDecimal netSalary = grossSalary.subtract(deductions).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            PayrollRecord payroll = existingPayrollMap.get(employee.getId());
            if (payroll == null) {
                payroll = new PayrollRecord();
                payroll.setEmployee(employee);
                payroll.setPayMonth(payMonth);
                payroll.setStatus(PayrollStatus.GENERATED);
                existingPayrollMap.put(employee.getId(), payroll);
            }

            payroll.setCtc(baseSalary.multiply(BigDecimal.valueOf(12)));
            payroll.setGrossSalary(grossSalary);
            payroll.setBasicSalary(basicSalary);
            payroll.setHra(hra);
            payroll.setBonus(BigDecimal.ZERO);
            payroll.setOvertime(overtimeAmount);
            payroll.setPf(pf);
            payroll.setTaxAmount(taxAmount);
            payroll.setLeaveDeduction(leaveDeduction);
            payroll.setOtherDeductions(BigDecimal.ZERO);
            payroll.setDeductions(deductions);
            payroll.setNetSalary(netSalary);

            payroll.setWorkingDays((int) records.stream().filter(r -> r.getCheckInTime() != null).count());
            payroll.setActualHours(BigDecimal.valueOf(workedMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
            payroll.setCasualLeave(BigDecimal.ONE);
            payroll.setRequiredHours(BigDecimal.valueOf(8));
            payroll.setExpectedWorkMinutes(expectedWorkMinutes);
            payroll.setWorkedMinutes(workedMinutes);
            payroll.setShortfallMinutes(shortfallMinutes);
            payroll.setOvertimeMinutes(overtimeMinutes);

            payrollToSave.add(payroll);
            if (payrollToSave.size() >= BATCH_SIZE) {
                payrollRepository.saveAll(payrollToSave);
                payrollToSave.clear();
            }
        }

        if (!payrollToSave.isEmpty()) {
            payrollRepository.saveAll(payrollToSave);
            payrollToSave.clear();
        }
        log.info("[AttendanceService] Calculated and saved payroll for employees for payMonth={}", payMonth);
    }

    @Transactional
    public int generatePayrollFromAttendance(String payMonth, String companyCode) {
        YearMonth month;
        try {
            month = YearMonth.parse(payMonth == null ? "" : payMonth.trim());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payMonth must use YYYY-MM format");
        }
        String company = companyCode == null ? "" : companyCode.trim();
        List<Employee> people = employeeRepository
                .findByActiveTrueAndLifecycleStatus(EmployeeLifecycleStatus.ACTIVE)
                .stream()
                .filter(employee -> company.isEmpty()
                        || (employee.getCompany() != null
                        && company.equalsIgnoreCase(employee.getCompany().getCode())))
                .toList();
        calculateAndSavePayrollForAttendance(new HashSet<>(people), month, month.toString());
        return people.size();
    }

    public byte[] sampleAttendanceWorkbook() {
        List<Employee> people = employeeRepository
                .findByActiveTrueAndLifecycleStatus(EmployeeLifecycleStatus.ACTIVE)
                .stream()
                .limit(6)
                .toList();

        LocalDate day = LocalDate.now(COMPANY_ZONE).withDayOfMonth(1);
        while (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Attendance");
            Row header = sheet.createRow(0);
            String[] titles = {"Employee Code", "Date", "Check In", "Check Out", "Monthly CTC"};
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
            }

            int rowIndex = 1;
            if (people.isEmpty()) {
                rowIndex = writeSampleRow(sheet, rowIndex, "CY-EMP-001", day, "09:00", "18:00", "720000");
                rowIndex = writeSampleRow(sheet, rowIndex, "CY-EMP-002", day.plusDays(1), "09:30", "18:30", "600000");
                writeSampleRow(sheet, rowIndex, "CY-EMP-003", day.plusDays(2), "10:00", "18:00", "540000");
            } else {
                for (Employee employee : people) {
                    BigDecimal annual = employee.getBaseSalary() == null
                            ? BigDecimal.valueOf(360000)
                            : employee.getBaseSalary().multiply(BigDecimal.valueOf(12));
                    String code = employee.getEmployeeCode() == null ? "CY-EMP-001" : employee.getEmployeeCode();
                    String annualText = annual.setScale(0, RoundingMode.HALF_UP).toPlainString();
                    rowIndex = writeSampleRow(sheet, rowIndex, code, day, "09:00", "18:00", annualText);
                    rowIndex = writeSampleRow(sheet, rowIndex, code, day.plusDays(1), "09:00", "17:00", annualText);
                    rowIndex = writeSampleRow(sheet, rowIndex, code, day.plusDays(2), "09:00", "19:00", annualText);
                }
            }

            for (int i = 0; i < titles.length; i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build the sample workbook");
        }
    }

    private static int writeSampleRow(
            Sheet sheet,
            int rowIndex,
            String code,
            LocalDate date,
            String checkIn,
            String checkOut,
            String ctc) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(code);
        row.createCell(1).setCellValue(date.toString());
        row.createCell(2).setCellValue(checkIn);
        row.createCell(3).setCellValue(checkOut);
        row.createCell(4).setCellValue(ctc);
        return rowIndex + 1;
    }

    /**
     * Peek at the workbook sheet names to decide whether the file is a
     * multi-sheet salary workbook (should go through ExcelPayrollService)
     * or a plain attendance file.
     */
    public boolean isSalaryWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("nexushr_chk_", ".xlsx");
            // Copy, do not transferTo: transferTo moves the upload, so the
            // later payroll import can no longer read the same MultipartFile.
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return isSalaryWorkbook(tempPath);
        } catch (Exception e) {
            log.warn("[AttendanceService] Could not check workbook sheets: {}", e.getMessage());
            return false;
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public boolean isSalaryWorkbook(Path path) throws IOException {
        List<String> names = CachedXlsx.sheetNames(path);
        if (names.isEmpty()) {
            return false;
        }
        if (names.size() > 1) {
            return true;
        }
        String name = names.get(0).toLowerCase(Locale.ENGLISH).strip();
        boolean signal = false;
        for (String candidate : SALARY_SHEET_SIGNALS) {
            if (name.contains(candidate)) {
                signal = true;
                break;
            }
        }
        if (!signal) {
            return false;
        }
        List<CachedXlsx.XlsxSheet> sheets = CachedXlsx.read(path, null);
        if (sheets.isEmpty()) {
            return false;
        }
        CachedXlsx.XlsxRow header = sheets.get(0).getRow(0);
        int columns = header == null ? 0 : header.getLastCellNum();
        return columns > 5;
    }

    public boolean isSalaryWorkbook(Workbook wb) {
        if (wb == null) return false;
        int numSheets = wb.getNumberOfSheets();
        // Single-sheet files with ≤ 5 columns are treated as attendance
        if (numSheets <= 1) {
            Sheet s = numSheets > 0 ? wb.getSheetAt(0) : null;
            if (s == null) return false;
            Row header = s.getRow(0);
            int cols = header == null ? 0 : header.getLastCellNum();
            if (cols <= 5) return false;
        }
        for (int i = 0; i < numSheets; i++) {
            String name = wb.getSheetName(i).toLowerCase(Locale.ENGLISH).strip();
            for (String signal : SALARY_SHEET_SIGNALS) {
                if (name.contains(signal)) return true;
            }
        }
        return numSheets > 1; // Multi-sheet → treat as salary workbook
    }

    @Transactional(readOnly = true)
    public AttendanceMonthlySummaryResponse monthlySummary(String payMonth) {

        YearMonth month = parsePayMonth(payMonth);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        int businessDays = HoursPayPolicy.expectedMinutes(start, end, false) / HoursPayPolicy.DAY_MINUTES;

        List<Employee> employees = employeeRepository
            .findByActiveTrueAndLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        // ── Bulk-load all month attendance & payroll in exactly 2 queries ─────────
        List<com.zidio.nexushr.domain.CompanyHoliday> holidayRows = companyHolidayCalendar == null
                ? List.of()
                : companyHolidayCalendar.between(start, end);
        List<AttendanceRecord> allMonthAttendance =
                attendanceRepository.findByAttendanceDateBetween(start, end);
        Map<Long, Integer> workedMinutesByEmployee = new HashMap<>();
        Map<Long, Integer> deductedMinutesByEmployee = new HashMap<>();
        for (AttendanceRecord r : allMonthAttendance) {
            if (r.getEmployee() != null && r.getEmployee().getId() != null
                    && r.getWorkMinutes() != null) {
                workedMinutesByEmployee.merge(r.getEmployee().getId(), clockedMinutes(r), Integer::sum);
                deductedMinutesByEmployee.merge(
                        r.getEmployee().getId(),
                        r.getDeductedMinutes() == null ? 0 : r.getDeductedMinutes(),
                        Integer::sum);
            }
        }

        List<PayrollRecord> allMonthPayroll = payrollRepository.findByPayMonth(payMonth);
        Map<Long, Integer> payrollWorkedMinutesByEmployee = new HashMap<>();
        for (PayrollRecord pr : allMonthPayroll) {
            if (pr.getEmployee() != null && pr.getEmployee().getId() != null
                    && pr.getWorkedMinutes() != null && pr.getWorkedMinutes() > 0) {
                payrollWorkedMinutesByEmployee.put(pr.getEmployee().getId(), pr.getWorkedMinutes());
            }
        }
        // ─────────────────────────────────────────────────────────────────────────

        List<AttendanceMonthlyEmployeeSummary> employeeSummaries = new ArrayList<>();

        long totalWorkedMinutes = 0;
        long totalExpectedMinutes = 0;
        long totalShortfallMinutes = 0;

        for (Employee employee : employees) {

            LocalDate effectiveStart = employee.getJoiningDate() != null
                && employee.getJoiningDate().isAfter(start)
                ? employee.getJoiningDate()
                : start;

            String companyCode = employee.getCompany() == null ? null : employee.getCompany().getCode();
            boolean gorle = HoursPayPolicy.gorle(companyCode);
            Set<LocalDate> holidays = companyHolidayCalendar == null
                    ? Set.of()
                    : companyHolidayCalendar.forCompany(holidayRows, companyCode);
            int expectedMinutes = HoursPayPolicy.expectedPaidMinutes(effectiveStart, end, gorle, holidays);

            int workedMinutes = Math.max(
                    0,
                    workedMinutesByEmployee.getOrDefault(employee.getId(), 0)
                            - deductedMinutesByEmployee.getOrDefault(employee.getId(), 0));

            // ── Fallback: if no daily attendance punches, use payroll record ──
            if (workedMinutes == 0) {
                workedMinutes = payrollWorkedMinutesByEmployee.getOrDefault(employee.getId(), 0);
                if (workedMinutes > 0) {
                    log.debug("[AttendanceService] monthlySummary: used payroll workedMinutes={} for employee={}",
                            workedMinutes, employee.getEmployeeCode());
                }
            }

            int shortfallMinutes = HoursPayPolicy.deductibleShortfall(expectedMinutes, workedMinutes);

            totalWorkedMinutes += workedMinutes;
            totalExpectedMinutes += expectedMinutes;
            totalShortfallMinutes += shortfallMinutes;

            employeeSummaries.add(
                new AttendanceMonthlyEmployeeSummary(
                    employee.getId(),
                    employee.getEmployeeCode(),
                    employee.getFullName(),
                    employee.getDepartment(),
                    workedMinutes,
                    expectedMinutes,
                    shortfallMinutes,
                    roundHours(workedMinutes),
                    roundHours(expectedMinutes),
                    roundHours(shortfallMinutes)
                )
            );
        }

        return new AttendanceMonthlySummaryResponse(
            payMonth,
            AttendanceRules.WORK_MINUTES,
            businessDays,
            employees.size(),
            totalWorkedMinutes,
            totalExpectedMinutes,
            totalShortfallMinutes,
            employeeSummaries
        );
    }

    public Long findEmployeeIdByUsername(String username) {

        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated username is required"
            );
        }

        Employee employee = employeeRepository
                .findByEmail(username)
                .orElseGet(() ->
                        employeeRepository
                                .findByEmployeeCode(username)
                                .orElseThrow(() ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Employee not found for authenticated user"
                                        )
                                )
                );

        return employee.getId();
    }

    private Employee getActiveEmployee(Long employeeId) {

        if (employeeId == null) {
            throw badRequest("Employee ID is required");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found: " + employeeId
                ));

        if (!employee.isActive()
                || employee.getLifecycleStatus() == EmployeeLifecycleStatus.OFFBOARDED
                || employee.getLifecycleStatus() == EmployeeLifecycleStatus.OFFBOARDING) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Attendance is available only for active employees"
            );
        }

        return employee;
    }

    private int calculateLateArrivalMinutes(LocalDateTime checkIn) {
        if (checkIn == null) {
            return 0;
        }
        return AttendanceRules.lateArrivalMinutes(checkIn.toLocalTime());
    }

    private int calculateOvertimeMinutes(int workMinutes) {
        if (workMinutes <= AttendanceRules.WORK_MINUTES) {
            return 0;
        }
        return workMinutes - AttendanceRules.WORK_MINUTES;
    }

    private LocalTime parseWorkdayStart() {

        try {
            return LocalTime.parse(workdayStart);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Invalid app.attendance.workday-start configuration: "
                            + workdayStart,
                    ex
            );
        }
    }

    private void validateCheckTimes(
            LocalDateTime checkIn,
            LocalDateTime checkOut) {

        if (checkOut.isBefore(checkIn)) {
            throw badRequest(
                    "Check-out time cannot be before check-in time"
            );
        }
    }

    private void validateDateMatchesAttendance(
            LocalDate date,
            LocalDateTime timestamp,
            String message) {

        if (!date.equals(timestamp.toLocalDate())) {
            throw badRequest(message);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private YearMonth parsePayMonth(String payMonth) {

        if (payMonth == null || payMonth.isBlank()) {
            throw badRequest("Pay month is required in YYYY-MM format");
        }

        try {
            return YearMonth.parse(payMonth.trim());
        } catch (DateTimeParseException ex) {
            throw badRequest("Pay month must be in YYYY-MM format");
        }
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {

        for (int cellIndex = 0; cellIndex <= 3; cellIndex++) {
            String value = formatter.formatCellValue(row.getCell(cellIndex));

            if (value != null && !value.trim().isBlank()) {
                return false;
            }
        }

        return true;
    }

    private LocalDate parseAttendanceDate(
            Cell cell,
            YearMonth month,
            int rowNumber) {

        if (cell == null) {
            throw badRequest("Attendance date is required in row " + rowNumber);
        }

        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        String raw = new DataFormatter(Locale.ENGLISH)
                .formatCellValue(cell)
                .trim();

        if (raw.isBlank()) {
            throw badRequest("Attendance date is required in row " + rowNumber);
        }

        try {
            if (raw.matches("^\\d{1,2}$")) {
                int day = Integer.parseInt(raw);
                return month.atDay(day);
            }

            return LocalDate.parse(raw);

        } catch (Exception ex) {
            throw badRequest("Invalid attendance date in row " + rowNumber);
        }
    }

    private LocalTime parseOptionalTime(Cell cell, DataFormatter formatter) {

        if (cell == null) {
            return null;
        }

        if (DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalTime().withSecond(0).withNano(0);
        }

        String raw = formatter.formatCellValue(cell).trim();

        if (raw.isBlank()) {
            return null;
        }

        try {
            return LocalTime.parse(raw.length() == 5 ? raw : normalizeTime(raw));
        } catch (Exception ex) {
            throw badRequest("Invalid time value: " + raw + " (expected HH:mm)");
        }
    }

    private String normalizeTime(String raw) {

        String candidate = raw.trim();

        if (candidate.matches("^\\d{1,2}:\\d{2}$")) {
            String[] parts = candidate.split(":");
            return String.format("%02d:%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        return candidate;
    }

    private String simplifyMessage(Exception ex) {

        if (ex instanceof ResponseStatusException statusException
                && statusException.getReason() != null
                && !statusException.getReason().isBlank()) {
            return statusException.getReason();
        }

        return ex.getMessage() == null
                ? "Invalid attendance row"
                : ex.getMessage();
    }

    private int businessDaysBetween(LocalDate start, LocalDate end) {

        if (start == null || end == null || start.isAfter(end)) {
            return 0;
        }

        int businessDays = 0;
        LocalDate date = start;

        while (!date.isAfter(end)) {
            DayOfWeek day = date.getDayOfWeek();

            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                businessDays++;
            }

            date = date.plusDays(1);
        }

        return businessDays;
    }

    private double roundHours(int minutes) {
        return Math.round((minutes / 60.0) * 100.0) / 100.0;
    }
}
