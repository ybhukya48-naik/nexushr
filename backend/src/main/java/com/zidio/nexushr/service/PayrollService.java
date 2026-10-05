package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.domain.PayrollStatus;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.service.NotificationService;
import com.zidio.nexushr.repository.PayrollRepository;
import com.zidio.nexushr.web.dto.PayrollAutoComponentsResponse;
import com.zidio.nexushr.web.dto.PayrollRequest;
import com.zidio.nexushr.web.dto.PayslipResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@Transactional
public class PayrollService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
        private final AttendanceRepository attendanceRepository;
    private final NotificationService notificationService;

    @Value("${app.payroll.tax-rate:0.10}")
    private BigDecimal taxRate;

        @Value("${app.attendance.standard-daily-minutes:480}")
        private int standardDailyMinutes;

    public PayrollService(
            PayrollRepository payrollRepository,
            EmployeeRepository employeeRepository,
                        AttendanceRepository attendanceRepository,
            NotificationService notificationService) {

        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
                this.attendanceRepository = attendanceRepository;
        this.notificationService = notificationService;
    }

    public PayrollRecord create(PayrollRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payroll request is required"
            );
        }

        if (request.getEmployeeId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee ID is required"
            );
        }

        validatePayMonth(request.getPayMonth());

        if (taxRate == null ||
                taxRate.compareTo(ZERO) < 0 ||
                taxRate.compareTo(BigDecimal.ONE) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Payroll tax rate must be between 0 and 1"
            );
        }

        // Validate request-provided payroll components before employee lookup.
        // This preserves clear 400 validation errors for invalid amounts.
        validateRequestAmount("Basic salary", request.getBasicSalary());
        validateRequestAmount("HRA", request.getHra());
        validateRequestAmount("Bonus", request.getBonus());
        validateRequestAmount("Overtime", request.getOvertime());
        validateRequestAmount("PF", request.getPf());
        validateRequestAmount("Leave deduction", request.getLeaveDeduction());
        validateRequestAmount("Other deductions", request.getOtherDeductions());

        Employee employee = employeeRepository.findById(
                request.getEmployeeId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Employee not found: " + request.getEmployeeId()
        ));

        if (!employee.isActive() ||
                employee.getLifecycleStatus() != EmployeeLifecycleStatus.ACTIVE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payroll can only be generated for an active employee"
            );
        }

        if (employee.getBaseSalary() == null ||
                employee.getBaseSalary().compareTo(ZERO) <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee base salary must be greater than zero"
            );
        }

        if (payrollRepository.existsByEmployee_IdAndPayMonth(
                employee.getId(),
                request.getPayMonth()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payroll already exists for employee "
                            + employee.getId()
                            + " and month "
                            + request.getPayMonth()
            );
        }

        BigDecimal basicSalary = amount(
                request.getBasicSalary() == null
                        ? employee.getBaseSalary()
                        : request.getBasicSalary()
        );

        boolean autoFromAttendance = Boolean.TRUE.equals(
                request.getAutoFromAttendance()
        );

        PayrollAutoComponentsResponse autoComponents = autoFromAttendance
                ? calculateAutoComponents(employee, request.getPayMonth(), basicSalary)
                : null;

        BigDecimal hra = amount(request.getHra());
        BigDecimal bonus = amount(request.getBonus());
        BigDecimal overtime = amount(
                request.getOvertime() == null && autoComponents != null
                        ? autoComponents.overtimeAmount()
                        : request.getOvertime()
        );
        BigDecimal pf = amount(request.getPf());
        BigDecimal leaveDeduction = amount(
                request.getLeaveDeduction() == null && autoComponents != null
                        ? autoComponents.leaveDeduction()
                        : request.getLeaveDeduction()
        );
        BigDecimal otherDeductions = amount(request.getOtherDeductions());

        validateNonNegative("Basic salary", basicSalary);
        validateNonNegative("HRA", hra);
        validateNonNegative("Bonus", bonus);
        validateNonNegative("Overtime", overtime);
        validateNonNegative("PF", pf);
        validateNonNegative("Leave deduction", leaveDeduction);
        validateNonNegative("Other deductions", otherDeductions);

        BigDecimal grossSalary = basicSalary
                .add(hra)
                .add(bonus)
                .add(overtime)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxAmount = grossSalary
                .multiply(taxRate)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal deductions = taxAmount
                .add(pf)
                .add(leaveDeduction)
                .add(otherDeductions)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netSalary = grossSalary
                .subtract(deductions)
                .setScale(2, RoundingMode.HALF_UP);

        if (netSalary.compareTo(ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Total deductions cannot exceed gross salary"
            );
        }

        PayrollRecord payrollRecord = new PayrollRecord();

        payrollRecord.setEmployee(employee);
        payrollRecord.setPayMonth(request.getPayMonth());

        payrollRecord.setBasicSalary(basicSalary);
        payrollRecord.setHra(hra);
        payrollRecord.setBonus(bonus);
        payrollRecord.setOvertime(overtime);

        payrollRecord.setGrossSalary(grossSalary);
        payrollRecord.setTaxAmount(taxAmount);
        payrollRecord.setPf(pf);
        payrollRecord.setLeaveDeduction(leaveDeduction);
        payrollRecord.setOtherDeductions(otherDeductions);

        payrollRecord.setDeductions(deductions);
        payrollRecord.setNetSalary(netSalary);
        payrollRecord.setStatus(PayrollStatus.GENERATED);

        return payrollRepository.save(payrollRecord);
    }

    @Transactional(readOnly = true)
    public PayrollAutoComponentsResponse autoComponents(
            Long employeeId,
            String payMonth) {

        if (employeeId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee ID is required"
            );
        }

        validatePayMonth(payMonth);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found: " + employeeId
                ));

        if (employee.getBaseSalary() == null
                || employee.getBaseSalary().compareTo(ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee base salary must be greater than zero"
            );
        }

        return calculateAutoComponents(
                employee,
                payMonth,
                employee.getBaseSalary()
        );
    }

    @Transactional(readOnly = true)
    public List<PayrollRecord> findAll() {
        return payrollRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<PayrollRecord> findByEmployee(Long employeeId) {

        validateEmployee(employeeId);

        return payrollRepository
                .findByEmployee_IdOrderByPayMonthDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public PayrollRecord findById(Long id) {

        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payroll ID is required"
            );
        }

        return payrollRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payroll not found: " + id
                ));
    }

    public PayrollRecord markPaid(Long id) {

        PayrollRecord payroll = findById(id);

        if (payroll.getStatus() == PayrollStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payroll is already marked as PAID"
            );
        }

        payroll.setStatus(PayrollStatus.PAID);

        PayrollRecord savedPayroll = payrollRepository.save(payroll);

        Employee employee = payroll.getEmployee();

        notificationService.sendNotification(
                employee.getId(),
                "Salary Credited",
                "Your salary for " + payroll.getPayMonth()
                        + " has been credited. Net salary: "
                        + payroll.getNetSalary(),
                NotificationType.GENERAL,
                NotificationChannel.BOTH,
                employee.getEmail(),
                employee.getPhone()
        );

        return savedPayroll;
    }

    @Transactional(readOnly = true)
    public PayslipResponse getPayslip(Long id) {

        PayrollRecord payroll = findById(id);
        Employee employee = payroll.getEmployee();

        return new PayslipResponse(
                payroll.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                payroll.getPayMonth(),
                payroll.getBasicSalary(),
                payroll.getHra(),
                payroll.getBonus(),
                payroll.getOvertime(),
                payroll.getGrossSalary(),
                payroll.getTaxAmount(),
                payroll.getPf(),
                payroll.getLeaveDeduction(),
                payroll.getOtherDeductions(),
                payroll.getDeductions(),
                payroll.getNetSalary(),
                payroll.getStatus().name()
        );
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null
                ? ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateRequestAmount(
            String field,
            BigDecimal value) {

        if (value != null) {
            validateNonNegative(field, value);
        }
    }

    private void validateNonNegative(
            String field,
            BigDecimal value) {

        if (value.compareTo(ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    field + " cannot be negative"
            );
        }
    }

    private void validateEmployee(Long employeeId) {

        if (employeeId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee ID is required"
            );
        }

        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found: " + employeeId
                ));
    }

    private void validatePayMonth(String payMonth) {

        if (payMonth == null || payMonth.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pay month is required"
            );
        }

        try {
            YearMonth.parse(payMonth);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pay month must use YYYY-MM format"
            );
        }
    }

    private PayrollAutoComponentsResponse calculateAutoComponents(
            Employee employee,
            String payMonth,
            BigDecimal basicSalary) {

        YearMonth month = YearMonth.parse(payMonth);
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        LocalDate effectiveStart = employee.getJoiningDate() != null
                && employee.getJoiningDate().isAfter(startDate)
                ? employee.getJoiningDate()
                : startDate;

        int businessDays = businessDaysBetween(effectiveStart, endDate);
        int expectedWorkMinutes = businessDays * standardDailyMinutes;

        List<AttendanceRecord> records = attendanceRepository
                .findByEmployee_IdAndAttendanceDateBetween(
                        employee.getId(),
                        startDate,
                        endDate
                );

        int workedMinutes = records.stream()
                .map(AttendanceRecord::getWorkMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int overtimeMinutes = records.stream()
                .map(AttendanceRecord::getOvertimeMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int shortfallMinutes = Math.max(0, expectedWorkMinutes - workedMinutes);

        if (expectedWorkMinutes <= 0) {
            return new PayrollAutoComponentsResponse(
                    employee.getId(),
                    employee.getEmployeeCode(),
                    payMonth,
                    businessDays,
                    0,
                    workedMinutes,
                    0,
                    overtimeMinutes,
                    ZERO.setScale(2, RoundingMode.HALF_UP),
                    ZERO.setScale(2, RoundingMode.HALF_UP)
            );
        }

        BigDecimal expectedMinutesValue = BigDecimal.valueOf(expectedWorkMinutes);
        BigDecimal perMinuteRate = basicSalary
                .divide(expectedMinutesValue, 8, RoundingMode.HALF_UP);

        BigDecimal leaveDeduction = perMinuteRate
                .multiply(BigDecimal.valueOf(shortfallMinutes))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal overtimeAmount = perMinuteRate
                .multiply(BigDecimal.valueOf(overtimeMinutes))
                .setScale(2, RoundingMode.HALF_UP);

        return new PayrollAutoComponentsResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                payMonth,
                businessDays,
                expectedWorkMinutes,
                workedMinutes,
                shortfallMinutes,
                overtimeMinutes,
                leaveDeduction,
                overtimeAmount
        );
    }

    private int businessDaysBetween(LocalDate start, LocalDate end) {

        if (start == null || end == null || start.isAfter(end)) {
            return 0;
        }

        int businessDays = 0;
        LocalDate date = start;

        while (!date.isAfter(end)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();

            if (dayOfWeek != DayOfWeek.SATURDAY
                    && dayOfWeek != DayOfWeek.SUNDAY) {
                businessDays++;
            }

            date = date.plusDays(1);
        }

        return businessDays;
    }
}
