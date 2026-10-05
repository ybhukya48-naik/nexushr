package com.zidio.nexushr.web.dto;

import java.util.List;

public record AttendanceMonthlySummaryResponse(
        String payMonth,
        int standardDailyMinutes,
        int businessDays,
        long totalEmployees,
        long totalWorkedMinutes,
        long totalExpectedMinutes,
        long totalShortfallMinutes,
        List<AttendanceMonthlyEmployeeSummary> employees) {
}
