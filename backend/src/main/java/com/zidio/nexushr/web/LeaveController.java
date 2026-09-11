package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.service.AttendanceService;
import com.zidio.nexushr.service.LeaveService;
import com.zidio.nexushr.web.dto.LeaveBalanceResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leaves")
public class LeaveController {

    private final LeaveService leaveService;
    private final AttendanceService attendanceService;

    public LeaveController(
            LeaveService leaveService,
            AttendanceService attendanceService) {

        this.leaveService = leaveService;
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public List<LeaveRequest> list(
            Authentication authentication) {

        if (authentication != null
                && authentication.getAuthorities().stream().anyMatch(
                authority ->
                        authority.getAuthority().equals("ROLE_HR")
                                || authority.getAuthority().equals("ROLE_ADMIN")
                                || authority.getAuthority().equals("ROLE_MANAGER"))) {

            return leaveService.findAll();
        }

        Long employeeId =
                attendanceService.findEmployeeIdByUsername(
                        authentication.getName()
                );

        return leaveService.findByEmployee(employeeId);
    }

    @PostMapping
    public LeaveRequest create(
            @RequestBody LeaveRequest request,
            Authentication authentication) {

        Long authenticatedEmployeeId =
                attendanceService.findEmployeeIdByUsername(
                        authentication.getName()
                );

        boolean management =
                authentication.getAuthorities().stream().anyMatch(
                        authority ->
                                authority.getAuthority().equals("ROLE_HR")
                                        || authority.getAuthority().equals("ROLE_ADMIN")
                                        || authority.getAuthority().equals("ROLE_MANAGER")
                );

        if (!management
                && (request.getEmployee() == null
                || request.getEmployee().getId() == null
                || !authenticatedEmployeeId.equals(
                request.getEmployee().getId()))) {

            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Employees can create leave only for themselves"
            );
        }

        return leaveService.create(request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public LeaveRequest updateStatus(
            @PathVariable Long id,
            @RequestParam LeaveStatus status) {

        return leaveService.updateStatus(id, status);
    }

    @GetMapping("/balance/{employeeId}")
    @PreAuthorize(
            "@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)"
    )
    public LeaveBalanceResponse getLeaveBalance(
            @PathVariable Long employeeId) {

        return leaveService.getLeaveBalance(employeeId);
    }

    @GetMapping("/balance/me")
    public LeaveBalanceResponse getMyLeaveBalance(
            Authentication authentication) {

        Long employeeId =
                attendanceService.findEmployeeIdByUsername(
                        authentication.getName()
                );

        return leaveService.getLeaveBalance(employeeId);
    }
}
