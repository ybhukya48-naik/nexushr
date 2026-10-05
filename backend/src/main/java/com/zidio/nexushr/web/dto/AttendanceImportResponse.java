package com.zidio.nexushr.web.dto;

import java.util.List;

public record AttendanceImportResponse(
        String payMonth,
        int totalRows,
        int importedCount,
        int updatedCount,
        int skippedCount,
        List<AttendanceImportRowError> errors) {
}
