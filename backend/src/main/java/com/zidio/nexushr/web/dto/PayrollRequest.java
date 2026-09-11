package com.zidio.nexushr.web.dto;

import java.math.BigDecimal;

public class PayrollRequest {

    private Long employeeId;
    private String payMonth;

    /*
     * Optional payroll components.
     *
     * Existing clients may omit these fields.
     * Omitted values are treated as zero by PayrollService.
     */
    private BigDecimal basicSalary;
    private BigDecimal hra;
    private BigDecimal bonus;
    private BigDecimal overtime;
    private BigDecimal pf;
    private BigDecimal leaveDeduction;
    private BigDecimal otherDeductions;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getPayMonth() {
        return payMonth;
    }

    public void setPayMonth(String payMonth) {
        this.payMonth = payMonth;
    }

    public BigDecimal getBasicSalary() {
        return basicSalary;
    }

    public void setBasicSalary(BigDecimal basicSalary) {
        this.basicSalary = basicSalary;
    }

    public BigDecimal getHra() {
        return hra;
    }

    public void setHra(BigDecimal hra) {
        this.hra = hra;
    }

    public BigDecimal getBonus() {
        return bonus;
    }

    public void setBonus(BigDecimal bonus) {
        this.bonus = bonus;
    }

    public BigDecimal getOvertime() {
        return overtime;
    }

    public void setOvertime(BigDecimal overtime) {
        this.overtime = overtime;
    }

    public BigDecimal getPf() {
        return pf;
    }

    public void setPf(BigDecimal pf) {
        this.pf = pf;
    }

    public BigDecimal getLeaveDeduction() {
        return leaveDeduction;
    }

    public void setLeaveDeduction(BigDecimal leaveDeduction) {
        this.leaveDeduction = leaveDeduction;
    }

    public BigDecimal getOtherDeductions() {
        return otherDeductions;
    }

    public void setOtherDeductions(BigDecimal otherDeductions) {
        this.otherDeductions = otherDeductions;
    }
}
