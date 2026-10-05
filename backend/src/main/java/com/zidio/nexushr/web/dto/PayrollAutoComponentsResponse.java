package com.zidio.nexushr.web.dto;

import java.math.BigDecimal;

public record PayrollAutoComponentsResponse(
        Long employeeId,
        String employeeCode,
        String payMonth,
        int businessDays,
        int expectedWorkMinutes,
        int workedMinutes,
        int shortfallMinutes,
        int overtimeMinutes,
        BigDecimal leaveDeduction,
        BigDecimal overtimeAmount) {
}
