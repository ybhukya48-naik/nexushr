package com.zidio.nexushr.web.dto;

public class LeaveBalanceResponse {

    private final Long employeeId;
    private final int annualEntitlement;
    private final long usedDays;
    private final long remainingDays;

    public LeaveBalanceResponse(
            Long employeeId,
            int annualEntitlement,
            long usedDays,
            long remainingDays) {

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

    public long getUsedDays() {
        return usedDays;
    }

    public long getRemainingDays() {
        return remainingDays;
    }
}
