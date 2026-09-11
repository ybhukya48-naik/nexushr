package com.zidio.nexushr.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "payroll_records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payroll_employee_month",
                        columnNames = {"employee_id", "pay_month"}
                )
        }
)
public class PayrollRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "pay_month", nullable = false, length = 20)
    private String payMonth;

    @Column(name = "basic_salary", precision = 15, scale = 2, nullable = false)
    private BigDecimal basicSalary;

    @Column(name = "hra", precision = 15, scale = 2, nullable = false)
    private BigDecimal hra;

    @Column(name = "bonus", precision = 15, scale = 2, nullable = false)
    private BigDecimal bonus;

    @Column(name = "overtime", precision = 15, scale = 2, nullable = false)
    private BigDecimal overtime;

    @Column(name = "gross_salary", precision = 15, scale = 2, nullable = false)
    private BigDecimal grossSalary;

    @Column(name = "tax_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal taxAmount;

    @Column(name = "pf", precision = 15, scale = 2, nullable = false)
    private BigDecimal pf;

    @Column(name = "leave_deduction", precision = 15, scale = 2, nullable = false)
    private BigDecimal leaveDeduction;

    @Column(name = "other_deductions", precision = 15, scale = 2, nullable = false)
    private BigDecimal otherDeductions;

    @Column(name = "deductions", precision = 15, scale = 2, nullable = false)
    private BigDecimal deductions;

    @Column(name = "net_salary", precision = 15, scale = 2, nullable = false)
    private BigDecimal netSalary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PayrollStatus status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
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

    public BigDecimal getGrossSalary() {
        return grossSalary;
    }

    public void setGrossSalary(BigDecimal grossSalary) {
        this.grossSalary = grossSalary;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
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

    public BigDecimal getDeductions() {
        return deductions;
    }

    public void setDeductions(BigDecimal deductions) {
        this.deductions = deductions;
    }

    public BigDecimal getNetSalary() {
        return netSalary;
    }

    public void setNetSalary(BigDecimal netSalary) {
        this.netSalary = netSalary;
    }

    public PayrollStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollStatus status) {
        this.status = status;
    }
}
