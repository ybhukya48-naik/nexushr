package com.zidio.nexushr.security;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.domain.PerformanceFeedback;
import com.zidio.nexushr.domain.PerformanceGoal;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.LeaveRequestRepository;
import com.zidio.nexushr.repository.NotificationRepository;
import com.zidio.nexushr.repository.PayrollRepository;
import com.zidio.nexushr.repository.PerformanceFeedbackRepository;
import com.zidio.nexushr.repository.PerformanceGoalRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("authorizationService")
public class AuthorizationService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollRepository payrollRepository;
    private final NotificationRepository notificationRepository;
    private final PerformanceGoalRepository performanceGoalRepository;
    private final PerformanceFeedbackRepository performanceFeedbackRepository;

    public AuthorizationService(
            EmployeeRepository employeeRepository,
            LeaveRequestRepository leaveRequestRepository,
            PayrollRepository payrollRepository,
            NotificationRepository notificationRepository,
            PerformanceGoalRepository performanceGoalRepository,
            PerformanceFeedbackRepository performanceFeedbackRepository) {

        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.payrollRepository = payrollRepository;
        this.notificationRepository = notificationRepository;
        this.performanceGoalRepository = performanceGoalRepository;
        this.performanceFeedbackRepository = performanceFeedbackRepository;
    }

    public boolean isSelf(
            Long employeeId,
            Authentication authentication) {

        if (employeeId == null || authentication == null) {
            return false;
        }

        if (hasManagementRole(authentication)) {
            return true;
        }

        Employee employee = findAuthenticatedEmployee(authentication);

        return employee != null
                && employee.getId().equals(employeeId);
    }

    public boolean isEmployeeSelfOrManagement(
            Long employeeId,
            Authentication authentication) {

        return isSelf(employeeId, authentication);
    }

    public boolean hasManagementRole(
            Authentication authentication) {

        if (authentication == null
                || authentication.getAuthorities() == null) {
            return false;
        }

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_HR")
                        || authority.getAuthority().equals("ROLE_MANAGER"));
    }

    public Long authenticatedEmployeeId(
            Authentication authentication) {

        Employee employee =
                findAuthenticatedEmployee(authentication);

        return employee == null
                ? null
                : employee.getId();
    }

    public boolean canAccessLeave(
            Long leaveId,
            Authentication authentication) {

        if (hasManagementRole(authentication)) {
            return true;
        }

        if (leaveId == null) {
            return false;
        }

        return leaveRequestRepository.findById(leaveId)
                .map(leave ->
                        isSelf(
                                leave.getEmployee().getId(),
                                authentication))
                .orElse(false);
    }

    public boolean canAccessPayroll(
            Long payrollId,
            Authentication authentication) {

        if (hasManagementRole(authentication)) {
            return true;
        }

        if (payrollId == null) {
            return false;
        }

        return payrollRepository.findById(payrollId)
                .map(payroll ->
                        isSelf(
                                payroll.getEmployee().getId(),
                                authentication))
                .orElse(false);
    }

    public boolean canAccessNotification(
            Long notificationId,
            Authentication authentication) {

        if (hasManagementRole(authentication)) {
            return true;
        }

        if (notificationId == null) {
            return false;
        }

        return notificationRepository.findById(notificationId)
                .map(notification ->
                        isSelf(
                                notification.getEmployeeId(),
                                authentication))
                .orElse(false);
    }

    public boolean canAccessGoal(
            Long goalId,
            Authentication authentication) {

        if (hasManagementRole(authentication)) {
            return true;
        }

        if (goalId == null) {
            return false;
        }

        return performanceGoalRepository.findById(goalId)
                .map(goal ->
                        isSelf(
                                goal.getEmployee().getId(),
                                authentication))
                .orElse(false);
    }

    public boolean canAccessFeedback(
            Long feedbackId,
            Authentication authentication) {

        if (hasManagementRole(authentication)) {
            return true;
        }

        if (feedbackId == null) {
            return false;
        }

        return performanceFeedbackRepository.findById(feedbackId)
                .map(feedback ->
                        isSelf(
                                feedback.getEmployee().getId(),
                                authentication)
                        || isSelf(
                                feedback.getReviewer().getId(),
                                authentication))
                .orElse(false);
    }

    public boolean canAccessReviewer(
            Long reviewerId,
            Authentication authentication) {

        return isSelf(reviewerId, authentication);
    }

    private Employee findAuthenticatedEmployee(
            Authentication authentication) {

        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            return null;
        }

        String username = authentication.getName();

        return employeeRepository.findByEmail(username)
                .orElseGet(() ->
                        employeeRepository
                                .findByEmployeeCode(username)
                                .orElse(null));
    }
}
