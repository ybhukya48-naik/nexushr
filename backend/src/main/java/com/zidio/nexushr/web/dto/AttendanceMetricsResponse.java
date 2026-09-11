package com.zidio.nexushr.web.dto;

import java.time.LocalDate;

public record AttendanceMetricsResponse(
        LocalDate date,
        long totalEmployees,
        long presentEmployees,
        long checkedOutEmployees,
        long lateEmployees,
        long overtimeEmployees,
        long totalWorkMinutes,
        long totalOvertimeMinutes) {
}
