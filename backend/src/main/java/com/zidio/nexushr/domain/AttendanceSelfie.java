package com.zidio.nexushr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "attendance_selfies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_selfie_punch",
                columnNames = {"employee_id", "attendance_date", "punch_type"}
        )
)
public class AttendanceSelfie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "content_type", nullable = false, length = 80)
    private String contentType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    @Column(name = "punch_type", nullable = false, length = 20)
    private String punchType = "CHECK_IN";

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }

    public String getPunchType() {
        return punchType;
    }

    public void setPunchType(String punchType) {
        this.punchType = punchType;
    }
}
