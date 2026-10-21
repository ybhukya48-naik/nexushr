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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.zidio.nexushr.domain.PayrollStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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

    @Value("${app.attendance.workday-start:09:00}")
    private String workdayStart = "09:00";

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

            int workMinutes = calculateWorkMinutes(checkIn, checkOut);

            attendanceRecord.setWorkMinutes(workMinutes);
            attendanceRecord.setOvertimeMinutes(
                    calculateOvertimeMinutes(workMinutes)
            );
        } else {
            attendanceRecord.setWorkMinutes(0);
            attendanceRecord.setOvertimeMinutes(0);
        }

        attendanceRecord.setLateArrivalMinutes(
                calculateLateArrivalMinutes(checkIn)
        );

        return attendanceRepository.save(attendanceRecord);
    }

    public AttendanceRecord checkIn(Long employeeId) {

        Employee employee = getActiveEmployee(employeeId);

        LocalDate today = LocalDate.now(COMPANY_ZONE);
        LocalDateTime now = LocalDateTime.now(COMPANY_ZONE);

        if (attendanceRepository
                .existsByEmployee_IdAndAttendanceDate(employeeId, today)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Employee has already checked in today"
            );
        }

        AttendanceRecord record = new AttendanceRecord();

        record.setEmployee(employee);
        record.setAttendanceDate(today);
        record.setCheckInTime(now);
        record.setCheckOutTime(null);
        record.setWorkMinutes(0);
        record.setLateArrivalMinutes(
                calculateLateArrivalMinutes(now)
        );
        record.setOvertimeMinutes(0);

        return attendanceRepository.save(record);
    }

    public AttendanceRecord checkOut(Long employeeId) {

        getActiveEmployee(employeeId);

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

        if (record.getCheckInTime() == null) {
            throw badRequest("Employee has not checked in");
        }

        if (record.getCheckOutTime() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Employee has already checked out today"
            );
        }

        LocalDateTime now = LocalDateTime.now(COMPANY_ZONE);

        validateCheckTimes(record.getCheckInTime(), now);

        record.setCheckOutTime(now);

        int workMinutes = calculateWorkMinutes(
                record.getCheckInTime(),
                now
        );

        record.setWorkMinutes(workMinutes);
        record.setOvertimeMinutes(
                calculateOvertimeMinutes(workMinutes)
        );

        if (record.getLateArrivalMinutes() == null) {
            record.setLateArrivalMinutes(
                    calculateLateArrivalMinutes(record.getCheckInTime())
            );
        }

        return attendanceRepository.save(record);
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

        return attendanceRepository.findByAttendanceDate(date);
    }

    public List<AttendanceRecord> listByEmployee(Long employeeId) {

        getActiveEmployee(employeeId);

        return attendanceRepository
                .findByEmployee_IdOrderByAttendanceDateDesc(employeeId);
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
                                : calculateWorkMinutes(checkIn, checkOut);

                        record.setWorkMinutes(workMinutes);
                        record.setLateArrivalMinutes(calculateLateArrivalMinutes(checkIn));
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

    /**
     * Calculate and update salary records for employees based on attendance worked days and hours.
     */
    private void calculateAndSavePayrollForAttendance(
            Set<Employee> employees,
            YearMonth month,
            String payMonth) {

        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

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

            int businessDays = businessDaysBetween(effectiveStart, endDate);
            int expectedWorkMinutes = businessDays * standardDailyMinutes;

            List<AttendanceRecord> records = attendanceByEmployee.getOrDefault(employee.getId(), List.of());

            int workedMinutes = records.stream()
                    .map(AttendanceRecord::getWorkMinutes)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

            int overtimeMinutes = records.stream()
                    .map(AttendanceRecord::getOvertimeMinutes)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

            int shortfallMinutes = Math.max(0, expectedWorkMinutes - workedMinutes);

            BigDecimal baseSalary = employee.getBaseSalary() != null && employee.getBaseSalary().compareTo(BigDecimal.ZERO) > 0
                    ? employee.getBaseSalary()
                    : BigDecimal.valueOf(30000);

            BigDecimal expectedMinutesVal = BigDecimal.valueOf(Math.max(1, expectedWorkMinutes));
            BigDecimal perMinuteRate = baseSalary.divide(expectedMinutesVal, 8, RoundingMode.HALF_UP);

            BigDecimal leaveDeduction = perMinuteRate
                    .multiply(BigDecimal.valueOf(shortfallMinutes))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal overtimeAmount = perMinuteRate
                    .multiply(BigDecimal.valueOf(overtimeMinutes))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal basicSalary = baseSalary.multiply(BigDecimal.valueOf(0.60)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal hra = baseSalary.multiply(BigDecimal.valueOf(0.40)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal grossSalary = baseSalary.add(overtimeAmount);

            BigDecimal pf = basicSalary.multiply(BigDecimal.valueOf(0.12)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = grossSalary.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal deductions = pf.add(leaveDeduction).add(taxAmount);
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
            payroll.setRequiredHours(BigDecimal.valueOf(9));
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
        int businessDays = businessDaysBetween(start, end);

        List<Employee> employees = employeeRepository
            .findByActiveTrueAndLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        // ── Bulk-load all month attendance & payroll in exactly 2 queries ─────────
        List<AttendanceRecord> allMonthAttendance =
                attendanceRepository.findByAttendanceDateBetween(start, end);
        Map<Long, Integer> workedMinutesByEmployee = new HashMap<>();
        for (AttendanceRecord r : allMonthAttendance) {
            if (r.getEmployee() != null && r.getEmployee().getId() != null
                    && r.getWorkMinutes() != null) {
                workedMinutesByEmployee.merge(r.getEmployee().getId(), r.getWorkMinutes(), Integer::sum);
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

            int employeeBusinessDays = businessDaysBetween(effectiveStart, end);
            int expectedMinutes = employeeBusinessDays * standardDailyMinutes;

            int workedMinutes = workedMinutesByEmployee.getOrDefault(employee.getId(), 0);

            // ── Fallback: if no daily attendance punches, use payroll record ──
            if (workedMinutes == 0) {
                workedMinutes = payrollWorkedMinutesByEmployee.getOrDefault(employee.getId(), 0);
                if (workedMinutes > 0) {
                    log.debug("[AttendanceService] monthlySummary: used payroll workedMinutes={} for employee={}",
                            workedMinutes, employee.getEmployeeCode());
                }
            }

            int shortfallMinutes = Math.max(0, expectedMinutes - workedMinutes);

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
            standardDailyMinutes,
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

        LocalTime start = parseWorkdayStart();

        if (!checkIn.toLocalTime().isAfter(start)) {
            return 0;
        }

        long minutes = Duration.between(
                start,
                checkIn.toLocalTime()
        ).toMinutes();

        return safeInteger(minutes);
    }

    private int calculateOvertimeMinutes(int workMinutes) {

        if (workMinutes <= standardDailyMinutes) {
            return 0;
        }

        return workMinutes - standardDailyMinutes;
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

    private int calculateWorkMinutes(
            LocalDateTime checkIn,
            LocalDateTime checkOut) {

        long minutes = Duration
                .between(checkIn, checkOut)
                .toMinutes();

        return safeInteger(minutes);
    }

    private int safeInteger(long value) {

        if (value < 0 || value > Integer.MAX_VALUE) {
            throw badRequest("Work duration is invalid");
        }

        return (int) value;
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
