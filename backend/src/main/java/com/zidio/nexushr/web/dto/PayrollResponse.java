package com.zidio.nexushr.web.dto;

import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.domain.PayrollStatus;

import java.math.BigDecimal;

public record PayrollResponse(
        Long id,
        EmployeeResponse employee,
        String payMonth,

        BigDecimal ctc,
        BigDecimal chargePerDay,
        Integer workingDays,
        BigDecimal requiredHours,
        BigDecimal actualHours,

        BigDecimal casualLeave,
        BigDecimal otherLeave,

        BigDecimal basicSalary,
        BigDecimal hra,
        BigDecimal bonus,
        BigDecimal overtime,

        BigDecimal grossSalary,
        BigDecimal earnedGross,

        BigDecimal conveyance,
        BigDecimal medical,
        BigDecimal others,

        BigDecimal taxAmount,
        BigDecimal pf,
        BigDecimal esi,
        BigDecimal pt,

        BigDecimal leaveDeduction,
        BigDecimal shortHoursDeduction,
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

                payroll.getCtc(),
                payroll.getChargePerDay(),
                payroll.getWorkingDays(),
                payroll.getRequiredHours(),
                payroll.getActualHours(),

                payroll.getCasualLeave(),
                payroll.getOtherLeave(),

                payroll.getBasicSalary(),
                payroll.getHra(),
                payroll.getBonus(),
                payroll.getOvertime(),

                payroll.getGrossSalary(),
                payroll.getEarnedGross(),

                payroll.getConveyance(),
                payroll.getMedical(),
                payroll.getOthers(),

                payroll.getTaxAmount(),
                payroll.getPf(),
                payroll.getEsi(),
                payroll.getPt(),

                payroll.getLeaveDeduction(),
                payroll.getShortHoursDeduction(),
                payroll.getOtherDeductions(),

                payroll.getDeductions(),
                payroll.getNetSalary(),

                payroll.getStatus()
        );
    }
}
