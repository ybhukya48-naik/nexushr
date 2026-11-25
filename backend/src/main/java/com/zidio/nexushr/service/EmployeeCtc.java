package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class EmployeeCtc {
    private EmployeeCtc() {}

    static BigDecimal yearly(Employee employee) {
        if (employee.getAnnualCtc() != null) {
            return employee.getAnnualCtc();
        }
        return employee.getBaseSalary() == null ? BigDecimal.ZERO
                : employee.getBaseSalary().max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(12));
    }

    static BigDecimal monthly(Employee employee) {
        return yearly(employee).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
}
