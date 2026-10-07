package com.zidio.nexushr.web.dto;

import java.math.BigDecimal;

public class PayslipResponse {

    private final Long payrollId;
    private final Long employeeId;
    private final String employeeCode;
    private final String employeeName;
    private final String payMonth;

    private final String companyCode;
    private final String companyName;
    private final String companyTagline;

    private final BigDecimal basicSalary;
    private final BigDecimal hra;
    private final BigDecimal bonus;
    private final BigDecimal overtime;

    private final BigDecimal grossSalary;
    private final BigDecimal taxAmount;
    private final BigDecimal pf;
    private final BigDecimal leaveDeduction;
    private final BigDecimal otherDeductions;
    private final BigDecimal deductions;
    private final BigDecimal netSalary;

    private final String status;

    public PayslipResponse(
            Long payrollId,
            Long employeeId,
            String employeeCode,
            String employeeName,
            String payMonth,
            String companyCode,
            String companyName,
            String companyTagline,
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
            String status) {

        this.payrollId = payrollId;
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.employeeName = employeeName;
        this.payMonth = payMonth;
        this.companyCode = companyCode;
        this.companyName = companyName;
        this.companyTagline = companyTagline;
        this.basicSalary = basicSalary;
        this.hra = hra;
        this.bonus = bonus;
        this.overtime = overtime;
        this.grossSalary = grossSalary;
        this.taxAmount = taxAmount;
        this.pf = pf;
        this.leaveDeduction = leaveDeduction;
        this.otherDeductions = otherDeductions;
        this.deductions = deductions;
        this.netSalary = netSalary;
        this.status = status;
    }

    public Long getPayrollId() { return payrollId; }

    public Long getEmployeeId() { return employeeId; }

    public String getEmployeeCode() { return employeeCode; }

    public String getEmployeeName() { return employeeName; }

    public String getPayMonth() { return payMonth; }

    public String getCompanyCode() { return companyCode; }

    public String getCompanyName() { return companyName; }

    public String getCompanyTagline() { return companyTagline; }

    public BigDecimal getBasicSalary() { return basicSalary; }

    public BigDecimal getHra() { return hra; }

    public BigDecimal getBonus() { return bonus; }

    public BigDecimal getOvertime() { return overtime; }

    public BigDecimal getGrossSalary() { return grossSalary; }

    public BigDecimal getTaxAmount() { return taxAmount; }

    public BigDecimal getPf() { return pf; }

    public BigDecimal getLeaveDeduction() { return leaveDeduction; }

    public BigDecimal getOtherDeductions() { return otherDeductions; }

    public BigDecimal getDeductions() { return deductions; }

    public BigDecimal getNetSalary() { return netSalary; }

    public String getStatus() { return status; }
}
