package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.service.AiInsightService;
import com.zidio.nexushr.service.EmployeeService;
import com.zidio.nexushr.web.dto.AiInsightResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiInsightsController {

    private final AiInsightService aiInsightService;
    private final EmployeeService employeeService;

    public AiInsightsController(AiInsightService aiInsightService, EmployeeService employeeService) {
        this.aiInsightService = aiInsightService;
        this.employeeService = employeeService;
    }

    @GetMapping("/attrition/{employeeId}")
    public AiInsightResponse attrition(@PathVariable Long employeeId) {
        Employee employee = employeeService.findById(employeeId);
        return aiInsightService.estimateAttrition(employee);
    }
}
