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
    @Column(name = "ctc", precision = 15, scale = 2, nullable = false)
    private BigDecimal ctc = BigDecimal.ZERO;

    @Column(name = "charge_per_day", precision = 15, scale = 2, nullable = false)
    private BigDecimal chargePerDay = BigDecimal.ZERO;

    @Column(name = "working_days", nullable = false)
    private Integer workingDays = 0;

    @Column(name = "required_hours", precision = 5, scale = 2, nullable = false)
    private BigDecimal requiredHours = BigDecimal.valueOf(9);

    @Column(name = "actual_hours", precision = 10, scale = 2, nullable = false)
    private BigDecimal actualHours = BigDecimal.ZERO;

    @Column(name = "casual_leave", precision = 5, scale = 2, nullable = false)
    private BigDecimal casualLeave = BigDecimal.ZERO;

    @Column(name = "other_leave", precision = 5, scale = 2, nullable = false)
    private BigDecimal otherLeave = BigDecimal.ZERO;

    @Column(name = "earned_gross", precision = 15, scale = 2, nullable = false)
    private BigDecimal earnedGross = BigDecimal.ZERO;

    @Column(name = "conveyance", precision = 15, scale = 2, nullable = false)
    private BigDecimal conveyance = BigDecimal.ZERO;

    @Column(name = "medical", precision = 15, scale = 2, nullable = false)
    private BigDecimal medical = BigDecimal.ZERO;

    @Column(name = "others", precision = 15, scale = 2, nullable = false)
    private BigDecimal others = BigDecimal.ZERO;

    @Column(name = "esi", precision = 15, scale = 2, nullable = false)
    private BigDecimal esi = BigDecimal.ZERO;

    @Column(name = "pt", precision = 15, scale = 2, nullable = false)
    private BigDecimal pt = BigDecimal.ZERO;

    @Column(name = "short_hours_deduction", precision = 15, scale = 2, nullable = false)
    private BigDecimal shortHoursDeduction = BigDecimal.ZERO;

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

    @Column(name = "expected_work_minutes", nullable = false)
    private Integer expectedWorkMinutes = 0;

    @Column(name = "worked_minutes", nullable = false)
    private Integer workedMinutes = 0;

    @Column(name = "shortfall_minutes", nullable = false)
    private Integer shortfallMinutes = 0;

    @Column(name = "overtime_minutes", nullable = false)
    private Integer overtimeMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PayrollStatus status;

    public Long getId() {
        return id;
    }
    public BigDecimal getCtc() {
        return ctc;
    }

    public void setCtc(BigDecimal ctc) {
        this.ctc = ctc;
    }

    public BigDecimal getChargePerDay() {
        return chargePerDay;
    }

    public void setChargePerDay(BigDecimal chargePerDay) {
        this.chargePerDay = chargePerDay;
    }

    public Integer getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(Integer workingDays) {
        this.workingDays = workingDays;
    }

    public BigDecimal getRequiredHours() {
        return requiredHours;
    }

    public void setRequiredHours(BigDecimal requiredHours) {
        this.requiredHours = requiredHours;
    }

    public BigDecimal getActualHours() {
        return actualHours;
    }

    public void setActualHours(BigDecimal actualHours) {
        this.actualHours = actualHours;
    }

    public BigDecimal getCasualLeave() {
        return casualLeave;
    }

    public void setCasualLeave(BigDecimal casualLeave) {
        this.casualLeave = casualLeave;
    }

    public BigDecimal getOtherLeave() {
        return otherLeave;
    }

    public void setOtherLeave(BigDecimal otherLeave) {
        this.otherLeave = otherLeave;
    }

    public BigDecimal getEarnedGross() {
        return earnedGross;
    }

    public void setEarnedGross(BigDecimal earnedGross) {
        this.earnedGross = earnedGross;
    }

    public BigDecimal getConveyance() {
        return conveyance;
    }

    public void setConveyance(BigDecimal conveyance) {
        this.conveyance = conveyance;
    }

    public BigDecimal getMedical() {
        return medical;
    }

    public void setMedical(BigDecimal medical) {
        this.medical = medical;
    }

    public BigDecimal getOthers() {
        return others;
    }

    public void setOthers(BigDecimal others) {
        this.others = others;
    }

    public BigDecimal getEsi() {
        return esi;
    }

    public void setEsi(BigDecimal esi) {
        this.esi = esi;
    }

    public BigDecimal getPt() {
        return pt;
    }

    public void setPt(BigDecimal pt) {
        this.pt = pt;
    }

    public BigDecimal getShortHoursDeduction() {
        return shortHoursDeduction;
    }

    public void setShortHoursDeduction(BigDecimal shortHoursDeduction) {
        this.shortHoursDeduction = shortHoursDeduction;
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

    public Integer getExpectedWorkMinutes() {
        return expectedWorkMinutes;
    }

    public void setExpectedWorkMinutes(Integer expectedWorkMinutes) {
        this.expectedWorkMinutes = expectedWorkMinutes;
    }

    public Integer getWorkedMinutes() {
        return workedMinutes;
    }

    public void setWorkedMinutes(Integer workedMinutes) {
        this.workedMinutes = workedMinutes;
    }

    public Integer getShortfallMinutes() {
        return shortfallMinutes;
    }

    public void setShortfallMinutes(Integer shortfallMinutes) {
        this.shortfallMinutes = shortfallMinutes;
    }

    public Integer getOvertimeMinutes() {
        return overtimeMinutes;
    }

    public void setOvertimeMinutes(Integer overtimeMinutes) {
        this.overtimeMinutes = overtimeMinutes;
    }

    public PayrollStatus getStatus() {
        return status;
    }

    public void setStatus(PayrollStatus status) {
        this.status = status;
    }
}



