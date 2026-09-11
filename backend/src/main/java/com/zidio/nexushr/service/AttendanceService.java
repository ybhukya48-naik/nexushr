package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.web.dto.AttendanceMetricsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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
}
