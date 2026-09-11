package com.zidio.nexushr.web.dto;

import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.domain.PayrollStatus;

import java.math.BigDecimal;

public record PayrollResponse(
        Long id,
        EmployeeResponse employee,
        String payMonth,
        BigDecimal basicSalary,
        BigDecimal hra,
        BigDecimal bonus,
        BigDecimal overtime,
        BigDecimal grossSalary,
        BigDecimal taxAmount,
        BigDecimal pf,
        BigDecimal leaveDeduction,
        BigDecimal otherDeductions,
        BigDecimal deductions,
        BigDecimal netSalary,
        PayrollStatus status
) {
    public static PayrollResponse from(PayrollRecord payroll) {
        return new PayrollResponse(
                payroll.getId(),
                EmployeeResponse.from(payroll.getEmployee()),
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
                payroll.getStatus()
        );
    }
}
