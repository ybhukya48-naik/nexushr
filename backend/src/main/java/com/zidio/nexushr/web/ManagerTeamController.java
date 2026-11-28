package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.LeaveRequestRepository;
import com.zidio.nexushr.repository.PayrollRepository;
import com.zidio.nexushr.security.AuthorizationService;
import com.zidio.nexushr.service.AttendanceRules;
import com.zidio.nexushr.service.AttendanceService;
import com.zidio.nexushr.service.EmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/manager")
public class ManagerTeamController {
    private final EmployeeService employees;
    private final AuthorizationService authorization;
    private final AttendanceRepository attendance;
    private final LeaveRequestRepository leaves;
    private final PayrollRepository payroll;

    public ManagerTeamController(EmployeeService employees, AuthorizationService authorization,
                                 AttendanceRepository attendance, LeaveRequestRepository leaves,
                                 PayrollRepository payroll) {
        this.employees = employees;
        this.authorization = authorization;
        this.attendance = attendance;
        this.leaves = leaves;
        this.payroll = payroll;
    }

    public record Day(LocalDate date, LocalDateTime checkIn, LocalDateTime checkOut,
                      boolean onLunch, int workedMinutes, int overtimeMinutes) {}
    public record TeamLeave(LocalDate startDate, LocalDate endDate, String type, boolean halfDay, String status) {}
    public record Member(Long id, String code, String name, String department, String designation,
                         int workedMinutes, int overtimeMinutes, String payrollStatus,
                         List<Day> attendance, List<TeamLeave> leave) {}
    public record TeamReport(String month, List<Member> members) {}

    @GetMapping("/team-report")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public TeamReport report(@RequestParam String month, Authentication authentication) {
        YearMonth selected;
        try {
            selected = YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must use YYYY-MM", ex);
        }
        if (selected.getYear() < 2000 || selected.getYear() > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must be between 2000 and 2100");
        }
        LocalDateTime now = LocalDateTime.now(AttendanceService.COMPANY_ZONE);
        Long managerId = authorization.authenticatedEmployeeId(authentication);
        List<Member> members = employees.findDirectReports(managerId).stream()
                .filter(employee -> authorization.canSeeEmployee(employee, authentication))
                .map(employee -> member(employee, selected, now))
                .toList();
        return new TeamReport(selected.toString(), members);
    }

    private Member member(Employee employee, YearMonth month, LocalDateTime now) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        List<Day> days = attendance.findByEmployee_IdAndAttendanceDateBetween(employee.getId(), start, end)
                .stream().map(record -> day(record, now))
                .sorted((a, b) -> b.date().compareTo(a.date()))
                .toList();
        List<TeamLeave> teamLeaves = leaves.findByEmployee_IdOrderByStartDateDesc(employee.getId()).stream()
                .filter(leave -> !leave.getStartDate().isAfter(end) && !leave.getEndDate().isBefore(start))
                .map(ManagerTeamController::leave).toList();
        String payrollStatus = payroll.findByEmployee_IdAndPayMonth(employee.getId(), month.toString())
                .map(record -> record.getStatus() == null ? "UNKNOWN" : record.getStatus().name())
                .orElse("NOT_GENERATED");
        return new Member(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                employee.getDepartment(), employee.getDesignation(),
                days.stream().mapToInt(Day::workedMinutes).sum(),
                days.stream().mapToInt(Day::overtimeMinutes).sum(), payrollStatus, days, teamLeaves);
    }

    private static Day day(AttendanceRecord record, LocalDateTime now) {
        int overtime = record.getOvertimeMinutes() == null ? 0 : Math.max(0, record.getOvertimeMinutes());
        return new Day(record.getAttendanceDate(), record.getCheckInTime(), record.getCheckOutTime(),
                record.getLunchStartedAt() != null, AttendanceRules.payrollMinutes(record, now), overtime);
    }

    private static TeamLeave leave(LeaveRequest leave) {
        return new TeamLeave(leave.getStartDate(), leave.getEndDate(), leave.getLeaveType(),
                leave.isHalfDay(), leave.getStatus().name());
    }
}
