package com.zidio.nexushr.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "employee_lifecycle_history")
public class EmployeeLifecycleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private EmployeeLifecycleStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private EmployeeLifecycleStatus toStatus;

    @Column(nullable = false)
    private String action;

    @Column(length = 1000)
    private String comments;

    private String changedBy;

    @Column(nullable = false)
    private LocalDateTime changedAt;

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

    public EmployeeLifecycleStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(EmployeeLifecycleStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public EmployeeLifecycleStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(EmployeeLifecycleStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
