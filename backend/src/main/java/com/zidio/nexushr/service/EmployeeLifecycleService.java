package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleHistory;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.repository.EmployeeLifecycleHistoryRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class EmployeeLifecycleService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeLifecycleHistoryRepository historyRepository;

    public EmployeeLifecycleService(
            EmployeeRepository employeeRepository,
            EmployeeLifecycleHistoryRepository historyRepository) {
        this.employeeRepository = employeeRepository;
        this.historyRepository = historyRepository;
    }

    public Employee submitOnboarding(Long employeeId, String changedBy, String comments) {
        Employee employee = getEmployee(employeeId);

        requireStatus(
                employee,
                EmployeeLifecycleStatus.PENDING_ONBOARDING
        );

        changeStatus(
                employee,
                EmployeeLifecycleStatus.PENDING_APPROVAL,
                "SUBMIT_ONBOARDING",
                changedBy,
                comments
        );

        return employeeRepository.save(employee);
    }

    public Employee approveOnboarding(Long employeeId, String changedBy, String comments) {
        Employee employee = getEmployee(employeeId);

        requireStatus(
                employee,
                EmployeeLifecycleStatus.PENDING_APPROVAL
        );

        changeStatus(
                employee,
                EmployeeLifecycleStatus.ACTIVE,
                "APPROVE_ONBOARDING",
                changedBy,
                comments
        );

        employee.setActive(true);

        return employeeRepository.save(employee);
    }

    public Employee startOffboarding(Long employeeId, String changedBy, String comments) {
        Employee employee = getEmployee(employeeId);

        requireStatus(
                employee,
                EmployeeLifecycleStatus.ACTIVE
        );

        changeStatus(
                employee,
                EmployeeLifecycleStatus.OFFBOARDING,
                "START_OFFBOARDING",
                changedBy,
                comments
        );

        return employeeRepository.save(employee);
    }

    public Employee completeOffboarding(Long employeeId, String changedBy, String comments) {
        Employee employee = getEmployee(employeeId);

        requireStatus(
                employee,
                EmployeeLifecycleStatus.OFFBOARDING
        );

        changeStatus(
                employee,
                EmployeeLifecycleStatus.OFFBOARDED,
                "COMPLETE_OFFBOARDING",
                changedBy,
                comments
        );

        employee.setActive(false);

        return employeeRepository.save(employee);
    }
    public Employee promote(
            Long employeeId,
            String changedBy,
            String comments,
            String newDesignation,
            java.math.BigDecimal newBaseSalary) {

        Employee employee = getEmployee(employeeId);

        requireStatus(employee, EmployeeLifecycleStatus.ACTIVE);

        if (newDesignation == null || newDesignation.isBlank()) {
            throw new IllegalArgumentException(
                    "New designation is required for promotion"
            );
        }

        if (newBaseSalary != null &&
                newBaseSalary.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "New base salary must be greater than zero"
            );
        }

        EmployeeLifecycleStatus currentStatus =
                employee.getLifecycleStatus();

        employee.setDesignation(newDesignation);

        if (newBaseSalary != null) {
            employee.setBaseSalary(newBaseSalary);
        }

        saveHistory(
                employee,
                currentStatus,
                currentStatus,
                "PROMOTION",
                changedBy,
                comments
        );

        return employeeRepository.save(employee);
    }

    public Employee transfer(
            Long employeeId,
            String changedBy,
            String comments,
            String newDepartment) {

        Employee employee = getEmployee(employeeId);

        requireStatus(employee, EmployeeLifecycleStatus.ACTIVE);

        if (newDepartment == null || newDepartment.isBlank()) {
            throw new IllegalArgumentException(
                    "New department is required for transfer"
            );
        }

        EmployeeLifecycleStatus currentStatus =
                employee.getLifecycleStatus();

        employee.setDepartment(newDepartment);

        saveHistory(
                employee,
                currentStatus,
                currentStatus,
                "TRANSFER",
                changedBy,
                comments
        );

        return employeeRepository.save(employee);
    }

    public Employee resign(
            Long employeeId,
            String changedBy,
            String comments) {

        Employee employee = getEmployee(employeeId);

        requireStatus(employee, EmployeeLifecycleStatus.ACTIVE);

        changeStatus(
                employee,
                EmployeeLifecycleStatus.OFFBOARDING,
                "RESIGNATION",
                changedBy,
                comments
        );

        return employeeRepository.save(employee);
    }

    private void saveHistory(
            Employee employee,
            EmployeeLifecycleStatus fromStatus,
            EmployeeLifecycleStatus toStatus,
            String action,
            String changedBy,
            String comments) {

        EmployeeLifecycleHistory history =
                new EmployeeLifecycleHistory();

        history.setEmployee(employee);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setAction(action);
        history.setChangedBy(changedBy);
        history.setComments(comments);
        history.setChangedAt(LocalDateTime.now());

        historyRepository.save(history);
    }
    @Transactional(readOnly = true)
    public List<EmployeeLifecycleHistory> getHistory(Long employeeId) {
        getEmployee(employeeId);

        return historyRepository
                .findByEmployeeIdOrderByChangedAtDesc(employeeId);
    }

    private Employee getEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Employee not found with id: " + employeeId
                        ));
    }

    private void requireStatus(
            Employee employee,
            EmployeeLifecycleStatus expectedStatus) {

        if (employee.getLifecycleStatus() != expectedStatus) {
            throw new IllegalStateException(
                    "Invalid lifecycle transition for employee "
                            + employee.getId()
                            + ". Expected status: "
                            + expectedStatus
                            + ", current status: "
                            + employee.getLifecycleStatus()
            );
        }
    }

    private void changeStatus(
            Employee employee,
            EmployeeLifecycleStatus newStatus,
            String action,
            String changedBy,
            String comments) {

        EmployeeLifecycleStatus oldStatus =
                employee.getLifecycleStatus();

        employee.setLifecycleStatus(newStatus);

        EmployeeLifecycleHistory history =
                new EmployeeLifecycleHistory();

        history.setEmployee(employee);
        history.setFromStatus(oldStatus);
        history.setToStatus(newStatus);
        history.setAction(action);
        history.setChangedBy(changedBy);
        history.setComments(comments);
        history.setChangedAt(LocalDateTime.now());

        historyRepository.save(history);
    }
}
