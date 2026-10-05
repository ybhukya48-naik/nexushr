package com.zidio.nexushr.web.dto;

public record AttendanceMonthlyEmployeeSummary(
        Long employeeId,
        String employeeCode,
        String fullName,
        String department,
        int workedMinutes,
        int expectedMinutes,
        int shortfallMinutes,
        double workedHours,
        double expectedHours,
        double shortfallHours) {
}
