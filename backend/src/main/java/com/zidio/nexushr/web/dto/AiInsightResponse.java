package com.zidio.nexushr.web.dto;

public record AiInsightResponse(
        Long employeeId,
        double attritionRisk,
        String riskBand,
        String recommendation
) {
}
