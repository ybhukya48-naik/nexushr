package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.security.AuthorizationService;
import com.zidio.nexushr.service.AiInsightService;
import com.zidio.nexushr.service.EmployeeService;
import com.zidio.nexushr.web.dto.AiInsightResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiInsightsController {

    private final AiInsightService aiInsightService;
    private final EmployeeService employeeService;
    private final AuthorizationService authorizationService;

    public AiInsightsController(
            AiInsightService aiInsightService,
            EmployeeService employeeService,
            AuthorizationService authorizationService) {

        this.aiInsightService = aiInsightService;
        this.employeeService = employeeService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/attrition/{employeeId}")
    public AiInsightResponse attrition(
            @PathVariable Long employeeId) {

        Employee employee =
                employeeService.findById(employeeId);

        return aiInsightService.estimateAttrition(employee);
    }

    @GetMapping("/skill-gap/{employeeId}")
    public Map<String, Object> skillGap(
            @PathVariable Long employeeId) {

        return aiInsightService.skillGap(employeeId);
    }

    @GetMapping("/engagement/{employeeId}")
    public Map<String, Object> engagement(
            @PathVariable Long employeeId) {

        return aiInsightService.engagement(employeeId);
    }

    @GetMapping("/recommendations/{employeeId}")
    public Map<String, Object> recommendations(
            @PathVariable Long employeeId) {

        return aiInsightService.recommendations(employeeId);
    }

    @GetMapping("/workforce-dashboard")
    public Map<String, Object> workforceDashboard(
            @RequestParam(required = false) String companyCode,
            Authentication authentication) {
        if (!authorizationService.seesAllCompanies(authentication)) {
            companyCode = authorizationService.ownCompanyCode(authentication);
        }

        return aiInsightService.workforceDashboard(companyCode);
    }
}
