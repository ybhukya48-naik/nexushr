package com.zidio.nexushr.web.dto;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.RoleType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        String employeeCode,
        String fullName,
        String email,
        String phone,
        String accountNumber,
        RoleType roleType,
        String department,
        String designation,
        LocalDate joiningDate,
        BigDecimal baseSalary,
        BigDecimal annualCtc,
        boolean active,
        EmployeeLifecycleStatus lifecycleStatus,
        String companyCode,
        String companyName,
        boolean siteVisitor,
        Long reportingManagerId,
        boolean hasProfilePhoto
) {

    public static EmployeeResponse from(Employee employee) {
        return from(employee, false);
    }

    public static EmployeeResponse withCompensation(Employee employee) {
        return from(employee, true);
    }

    private static EmployeeResponse from(Employee employee, boolean includeCompensation) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getAccountNumber(),
                employee.getRoleType(),
                employee.getDepartment(),
                employee.getDesignation(),
                employee.getJoiningDate(),
                employee.getBaseSalary(),
                includeCompensation ? employee.getAnnualCtc() : null,
                employee.isActive(),
                employee.getLifecycleStatus(),
                employee.getCompany() == null ? null : employee.getCompany().getCode(),
                employee.getCompany() == null ? null : employee.getCompany().getName(),
                employee.isSiteVisitor(),
                employee.getReportingManagerId(),
                employee.hasProfilePhoto()
        );
    }
}
