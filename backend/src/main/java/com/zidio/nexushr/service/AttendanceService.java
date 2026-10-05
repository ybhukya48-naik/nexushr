package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    @Value("${app.attendance.standard-daily-minutes:480}")
    private int standardDailyMinutes = 480;

    @Value("${app.attendance.workday-start:09:00}")
    private String workdayStart = "09:00";

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            EmployeeRepository employeeRepository) {

        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
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

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

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

        LocalDate today = LocalDate.now();

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

        LocalDateTime now = LocalDateTime.now();

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

            int totalRows = 0;
            int importedCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;

            List<AttendanceImportRowError> errors = new ArrayList<>();
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);

            try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {

                Sheet sheet = workbook.getNumberOfSheets() > 0
                    ? workbook.getSheetAt(0)
                    : null;

                if (sheet == null) {
                throw badRequest("Excel sheet is missing");
                }

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

                    Employee employee = employeeRepository
                        .findByEmployeeCodeIgnoreCase(employeeCode)
                        .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Employee not found for code: " + employeeCode
                        ));

                    if (!employee.isActive()
                        || employee.getLifecycleStatus() != EmployeeLifecycleStatus.ACTIVE) {
                    throw badRequest("Employee is not active: " + employeeCode);
                    }

                    AttendanceRecord record = attendanceRepository
                        .findByEmployee_IdAndAttendanceDate(
                            employee.getId(),
                            attendanceDate
                        )
                        .orElseGet(AttendanceRecord::new);

                    boolean exists = record.getId() != null;

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

                    attendanceRepository.save(record);

                    if (exists) {
                    updatedCount++;
                    } else {
                    importedCount++;
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

            } catch (IOException ex) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to read Excel file",
                    ex
                );
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

            @Transactional(readOnly = true)
            public AttendanceMonthlySummaryResponse monthlySummary(String payMonth) {

            YearMonth month = parsePayMonth(payMonth);
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();
            int businessDays = businessDaysBetween(start, end);

            List<Employee> employees = employeeRepository
                .findByActiveTrueAndLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

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

                int workedMinutes = attendanceRepository
                    .findByEmployee_IdAndAttendanceDateBetween(
                        employee.getId(),
                        start,
                        end
                    )
                    .stream()
                    .map(AttendanceRecord::getWorkMinutes)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

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
