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

        long requestedDays = ChronoUnit.DAYS.between(
                request.getStartDate(),
                request.getEndDate()
        ) + 1;

        long usedDays = getApprovedLeaveDays(employee.getId());

        if (usedDays + requestedDays > annualEntitlementDays) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Leave request exceeds the remaining annual leave balance"
            );
        }

        request.setEmployee(employee);

        // Clients cannot create pre-approved/pre-rejected leave.
        request.setStatus(LeaveStatus.PENDING);

        LeaveRequest saved = leaveRequestRepository.save(request);

        notifyManagers(saved);

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

            long requestedDays = request.getDurationDays();
            long usedDays = getApprovedLeaveDays(
                    request.getEmployee().getId()
            );

            if (usedDays + requestedDays > annualEntitlementDays) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Approving this leave would exceed the annual leave balance"
                );
            }
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

        long usedDays = getApprovedLeaveDays(employeeId);

        long remainingDays = Math.max(
                0,
                (long) annualEntitlementDays - usedDays
        );

        return new LeaveBalanceResponse(
                employeeId,
                annualEntitlementDays,
                usedDays,
                remainingDays
        );
    }

    private long getApprovedLeaveDays(Long employeeId) {

        return leaveRequestRepository
                .findByEmployee_IdAndStatus(
                        employeeId,
                        LeaveStatus.APPROVED
                )
                .stream()
                .mapToLong(leave ->
                        ChronoUnit.DAYS.between(
                                leave.getStartDate(),
                                leave.getEndDate()
                        ) + 1
                )
                .sum();
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

    private void notifyManagers(LeaveRequest request) {

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

        List<Employee> managers =
                employeeRepository.findByRoleTypeAndActiveTrue(
                        RoleType.MANAGER
                );

        for (Employee manager : managers) {
            notificationService.sendNotification(
                    manager.getId(),
                    "Leave approval required",
                    message,
                    NotificationType.APPROVAL,
                    NotificationChannel.IN_APP,
                    manager.getEmail(),
                    manager.getPhone()
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
                NotificationChannel.IN_APP,
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
