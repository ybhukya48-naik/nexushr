package com.zidio.nexushr.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "attendance_records",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_attendance_employee_date",
            columnNames = {"employee_id", "attendance_date"}
        )
    },
    indexes = {
        @Index(
            name = "idx_attendance_date",
            columnList = "attendance_date"
        ),
        @Index(
            name = "idx_attendance_employee_date",
            columnList = "employee_id, attendance_date"
        )
    }
)
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "work_minutes", nullable = false)
    private Integer workMinutes = 0;

    @Column(name = "late_arrival_minutes", nullable = false)
    private Integer lateArrivalMinutes = 0;

    @Column(name = "overtime_minutes", nullable = false)
    private Integer overtimeMinutes = 0;

    @Column(name = "session_started_at")
    private LocalDateTime sessionStartedAt;

    @Column(name = "closed_work_minutes", nullable = false)
    private Integer closedWorkMinutes = 0;

    @Column(name = "break_minutes", nullable = false)
    private Integer breakMinutes = 0;

    @Column(name = "attendance_status", length = 20)
    private String attendanceStatus;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Column(name = "session_idle_minutes", nullable = false)
    private Integer sessionIdleMinutes = 0;

    @Column(name = "late_warning", nullable = false)
    private Boolean lateWarning = false;

    @Column(name = "yellow_flag", nullable = false)
    private Boolean yellowFlag = false;

    @Column(name = "red_flag", nullable = false)
    private Boolean redFlag = false;

    @Column(name = "blue_flag", nullable = false)
    private Boolean blueFlag = false;

    @Column(name = "deducted_minutes", nullable = false)
    private Integer deductedMinutes = 0;

    @Column(name = "penalty_note", length = 500)
    private String penaltyNote;

    @Column(name = "selfie_captured", nullable = false)
    private Boolean selfieCaptured = false;

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

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public LocalDateTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(LocalDateTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public LocalDateTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(LocalDateTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public Integer getWorkMinutes() {
        return workMinutes;
    }

    public void setWorkMinutes(Integer workMinutes) {
        this.workMinutes = workMinutes;
    }

    public Integer getLateArrivalMinutes() {
        return lateArrivalMinutes;
    }

    public void setLateArrivalMinutes(Integer lateArrivalMinutes) {
        this.lateArrivalMinutes = lateArrivalMinutes;
    }

    public Integer getOvertimeMinutes() {
        return overtimeMinutes;
    }

    public void setOvertimeMinutes(Integer overtimeMinutes) {
        this.overtimeMinutes = overtimeMinutes;
    }

    public LocalDateTime getSessionStartedAt() {
        return sessionStartedAt;
    }

    public void setSessionStartedAt(LocalDateTime sessionStartedAt) {
        this.sessionStartedAt = sessionStartedAt;
    }

    public Integer getClosedWorkMinutes() {
        return closedWorkMinutes;
    }

    public void setClosedWorkMinutes(Integer closedWorkMinutes) {
        this.closedWorkMinutes = closedWorkMinutes;
    }

    public Integer getBreakMinutes() {
        return breakMinutes;
    }

    public void setBreakMinutes(Integer breakMinutes) {
        this.breakMinutes = breakMinutes;
    }

    public String getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public Integer getSessionIdleMinutes() {
        return sessionIdleMinutes;
    }

    public void setSessionIdleMinutes(Integer sessionIdleMinutes) {
        this.sessionIdleMinutes = sessionIdleMinutes;
    }

    public Boolean getLateWarning() {
        return lateWarning;
    }

    public void setLateWarning(Boolean lateWarning) {
        this.lateWarning = lateWarning;
    }

    public Boolean getYellowFlag() {
        return yellowFlag;
    }

    public void setYellowFlag(Boolean yellowFlag) {
        this.yellowFlag = yellowFlag;
    }

    public Boolean getRedFlag() {
        return redFlag;
    }

    public void setRedFlag(Boolean redFlag) {
        this.redFlag = redFlag;
    }

    public Boolean getBlueFlag() {
        return blueFlag;
    }

    public void setBlueFlag(Boolean blueFlag) {
        this.blueFlag = blueFlag;
    }

    public Integer getDeductedMinutes() {
        return deductedMinutes;
    }

    public void setDeductedMinutes(Integer deductedMinutes) {
        this.deductedMinutes = deductedMinutes;
    }

    public String getPenaltyNote() {
        return penaltyNote;
    }

    public void setPenaltyNote(String penaltyNote) {
        this.penaltyNote = penaltyNote;
    }

    public Boolean getSelfieCaptured() {
        return selfieCaptured;
    }

    public void setSelfieCaptured(Boolean selfieCaptured) {
        this.selfieCaptured = selfieCaptured;
    }
}
