package com.zidio.nexushr.web.dto;

import java.math.BigDecimal;

public class LeaveBalanceResponse {

    private final Long employeeId;
    private final int annualEntitlement;
    private final BigDecimal usedDays;
    private final BigDecimal remainingDays;

    public LeaveBalanceResponse(
            Long employeeId,
            int annualEntitlement,
            BigDecimal usedDays,
            BigDecimal remainingDays) {

        this.employeeId = employeeId;
        this.annualEntitlement = annualEntitlement;
        this.usedDays = usedDays;
        this.remainingDays = remainingDays;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public int getAnnualEntitlement() {
        return annualEntitlement;
    }

    public BigDecimal getUsedDays() {
        return usedDays;
    }

    public BigDecimal getRemainingDays() {
        return remainingDays;
    }
}
