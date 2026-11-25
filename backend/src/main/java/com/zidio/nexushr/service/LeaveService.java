package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.LeaveRequestRepository;
import com.zidio.nexushr.web.dto.LeaveBalanceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.List;

@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    @Value("${app.leave.annual-entitlement-days:24}")
    private int annualEntitlementDays = 24;

    public LeaveService(
            LeaveRequestRepository leaveRequestRepository,
            EmployeeRepository employeeRepository,
            NotificationService notificationService) {

        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
    }

    public LeaveRequest create(LeaveRequest request) {

        if (request == null) {
            throw badRequest("Leave request is required");
        }

        if (request.getEmployee() == null
                || request.getEmployee().getId() == null) {
            throw badRequest("Employee ID is required");
        }

        if (request.getStartDate() == null) {
            throw badRequest("Start date is required");
        }

        if (request.getEndDate() == null) {
            throw badRequest("End date is required");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw badRequest(
                    "Start date cannot be after end date"
            );
        }
        if (request.isHalfDay() && !request.getStartDate().equals(request.getEndDate())) {
            throw badRequest("A half-day leave must be on a single date");
        }

        if (request.getReason() == null
                || request.getReason().isBlank()) {
            throw badRequest("Leave reason is required");
        }

        Employee employee = employeeRepository
                .findById(request.getEmployee().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found: "
                                + request.getEmployee().getId()
                ));

        validateActiveEmployee(employee);

        boolean overlapping =
                leaveRequestRepository.existsOverlappingLeave(
                        employee.getId(),
                        request.getStartDate(),
                        request.getEndDate(),
                        List.of(
                                LeaveStatus.PENDING,
                                LeaveStatus.APPROVED
                        )
                );

        if (overlapping) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Leave request overlaps an existing pending or approved leave"
            );
        }

        request.setLeaveType(normalizeLeaveType(request.getLeaveType()));
        enforceLeavePolicy(employee.getId(), request);

        request.setEmployee(employee);

        // Clients cannot create pre-approved/pre-rejected leave.
        request.setStatus(LeaveStatus.PENDING);

        LeaveRequest saved = leaveRequestRepository.save(request);

        notifyApprovers(saved);

        return saved;
    }

    public List<LeaveRequest> findAll() {
        return leaveRequestRepository.findAll();
    }

    public List<LeaveRequest> findByEmployee(Long employeeId) {

        if (employeeId == null) {
            throw badRequest("Employee ID is required");
        }

        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found: " + employeeId
                ));

        return leaveRequestRepository
                .findByEmployee_IdOrderByStartDateDesc(employeeId);
    }

    public LeaveRequest updateStatus(
            Long id,
            LeaveStatus status) {

        if (id == null) {
            throw badRequest("Leave request ID is required");
        }

        if (status == null) {
            throw badRequest("Leave status is required");
        }

        if (status != LeaveStatus.APPROVED
                && status != LeaveStatus.REJECTED) {
            throw badRequest(
                    "Leave status must be APPROVED or REJECTED"
            );
        }

        LeaveRequest request =
                leaveRequestRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Leave request not found: " + id
                        ));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending leave requests can be approved or rejected"
            );
        }

        if (status == LeaveStatus.APPROVED) {
            enforceLeavePolicy(request.getEmployee().getId(), request);
        }

        request.setStatus(status);

        LeaveRequest saved =
                leaveRequestRepository.save(request);

        notifyEmployeeStatusChange(saved);

        return saved;
    }

    public LeaveBalanceResponse getLeaveBalance(Long employeeId) {

        if (employeeId == null) {
            throw badRequest("Employee ID is required");
        }

        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found: " + employeeId
                ));

        BigDecimal usedDays = getApprovedLeaveDays(employeeId);
        BigDecimal remainingDays = BigDecimal.valueOf(annualEntitlementDays).subtract(usedDays).max(BigDecimal.ZERO);

        return new LeaveBalanceResponse(
                employeeId,
                annualEntitlementDays,
                usedDays,
                remainingDays
        );
    }

    private boolean isDutyRequest(String type) {
        return "WFH".equals(type)
                || "SITE_MORNING".equals(type)
                || "SITE_AFTERNOON".equals(type)
                || "SITE_EVENING".equals(type);
    }

    private String normalizeLeaveType(String leaveType) {
        if (leaveType == null || leaveType.isBlank()) {
            return "";
        }
        return leaveType.trim().toUpperCase();
    }

    private void enforceLeavePolicy(Long employeeId, LeaveRequest request) {
        String type = normalizeLeaveType(request.getLeaveType());
        if (isDutyRequest(type)) {
            return;
        }
        BigDecimal requestedDays = leaveDays(request);
        BigDecimal usedDays = getApprovedLeaveDays(employeeId);
        if (usedDays.add(requestedDays).compareTo(BigDecimal.valueOf(annualEntitlementDays)) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Leave request exceeds the remaining annual leave balance"
            );
        }
        if (!"CASUAL".equals(type) && !"EARNED".equals(type)) {
            return;
        }
        LocalDate monthStart = request.getStartDate().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        List<LeaveRequest> existing = leaveRequestRepository
                .findByEmployee_IdOrderByStartDateDesc(employeeId)
                .stream()
                .filter(leave -> leave.getId() == null || request.getId() == null || !leave.getId().equals(request.getId()))
                .filter(leave -> leave.getStatus() == LeaveStatus.APPROVED || leave.getStatus() == LeaveStatus.PENDING)
                .filter(leave -> type.equals(normalizeLeaveType(leave.getLeaveType())))
                .filter(leave -> !leave.getEndDate().isBefore(monthStart) && !leave.getStartDate().isAfter(monthEnd))
                .toList();
        boolean anotherHalfCasual = "CASUAL".equals(type) && request.isHalfDay()
                && existing.size() == 1 && existing.get(0).isHalfDay();
        if (!existing.isEmpty() && !anotherHalfCasual) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "CASUAL".equals(type)
                            ? "One casual leave is allowed each month"
                            : "One earned leave request is allowed each month"
            );
        }
    }

    private BigDecimal getApprovedLeaveDays(Long employeeId) {

        return leaveRequestRepository
                .findByEmployee_IdAndStatus(
                        employeeId,
                        LeaveStatus.APPROVED
                )
                .stream()
                .filter(leave -> {
                    String type = normalizeLeaveType(leave.getLeaveType());
                    return !isDutyRequest(type);
                })
                .map(this::leaveDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal leaveDays(LeaveRequest leave) {
        return leave.isHalfDay() ? new BigDecimal("0.5")
                : BigDecimal.valueOf(ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate()) + 1);
    }

    private void validateActiveEmployee(Employee employee) {

        if (!employee.isActive()
                || employee.getLifecycleStatus() == EmployeeLifecycleStatus.OFFBOARDING
                || employee.getLifecycleStatus() == EmployeeLifecycleStatus.OFFBOARDED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Leave is available only for active employees"
            );
        }
    }

    private void notifyApprovers(LeaveRequest request) {

        if (notificationService == null) {
            return;
        }

        String employeeName =
                request.getEmployee().getFullName();

        String message =
                "Leave request from "
                        + employeeName
                        + " for "
                        + request.getStartDate()
                        + " to "
                        + request.getEndDate()
                        + " is pending approval.";

        List<Employee> approvers = new java.util.ArrayList<>();
        approvers.addAll(employeeRepository.findByRoleTypeAndActiveTrue(RoleType.HR));
        approvers.addAll(employeeRepository.findByRoleTypeAndActiveTrue(RoleType.ADMIN));
        approvers.addAll(employeeRepository.findByRoleTypeAndActiveTrue(RoleType.SUPER_ADMIN));
        String company = request.getEmployee().getCompany() == null
                ? null : request.getEmployee().getCompany().getCode();
        for (Employee approver : approvers) {
            if (approver.getRoleType() != RoleType.SUPER_ADMIN
                    && (company == null || approver.getCompany() == null
                    || !company.equalsIgnoreCase(approver.getCompany().getCode()))) {
                continue;
            }
            notificationService.sendNotification(
                    approver.getId(),
                    "Leave approval required",
                    message,
                    NotificationType.APPROVAL,
                    NotificationChannel.IN_APP,
                    approver.getEmail(),
                    approver.getPhone()
            );
        }
    }

    private void notifyEmployeeStatusChange(
            LeaveRequest request) {

        if (notificationService == null
                || request.getEmployee() == null) {
            return;
        }

        String title =
                request.getStatus() == LeaveStatus.APPROVED
                        ? "Leave approved"
                        : "Leave rejected";

        String message =
                "Your leave request from "
                        + request.getStartDate()
                        + " to "
                        + request.getEndDate()
                        + " has been "
                        + request.getStatus().name().toLowerCase()
                        + ".";

        notificationService.sendNotification(
                request.getEmployee().getId(),
                title,
                message,
                NotificationType.APPROVAL,
                NotificationChannel.BOTH,
                request.getEmployee().getEmail(),
                request.getEmployee().getPhone()
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
