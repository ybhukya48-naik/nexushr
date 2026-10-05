package com.zidio.nexushr.web.dto;

public record AttendanceImportRowError(
        int rowNumber,
        String employeeCode,
        String message) {
}
