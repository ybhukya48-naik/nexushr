package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.AiInsightService;
import com.zidio.nexushr.service.EmployeeService;
import com.zidio.nexushr.web.dto.AiInsightResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiInsightsController.class)
@Import(SecurityConfig.class)
class AiInsightsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiInsightService aiInsightService;

    @MockitoBean
    private EmployeeService employeeService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    @WithMockUser
    void attrition_returnsInsightForEmployee() throws Exception {
        Employee employee = new Employee();
        employee.setId(1L);
        AiInsightResponse response = new AiInsightResponse(1L, 0.15, "LOW",
                "Maintain engagement with recognition and progression checkpoints.");

        when(employeeService.findById(1L)).thenReturn(employee);
        when(aiInsightService.estimateAttrition(any(Employee.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/ai/attrition/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.riskBand").value("LOW"))
                .andExpect(jsonPath("$.attritionRisk").value(0.15));
    }

    @Test
    @WithMockUser
    void attrition_highRiskEmployee_returnsHighBand() throws Exception {
        Employee employee = new Employee();
        employee.setId(2L);
        AiInsightResponse response = new AiInsightResponse(2L, 0.95, "HIGH",
                "Schedule manager 1:1, compensation calibration, and targeted retention plan.");

        when(employeeService.findById(2L)).thenReturn(employee);
        when(aiInsightService.estimateAttrition(any(Employee.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/ai/attrition/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskBand").value("HIGH"))
                .andExpect(jsonPath("$.attritionRisk").value(0.95));
    }

    @Test
    void attrition_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/attrition/1"))
                .andExpect(status().is4xxClientError());
    }
}
