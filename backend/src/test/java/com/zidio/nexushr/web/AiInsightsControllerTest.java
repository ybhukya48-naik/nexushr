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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

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

    // ============================================================
    // ATTRITION
    // ============================================================

    @Test
    @WithMockUser(roles = "HR")
    void attrition_returnsInsightForEmployee() throws Exception {
        Employee employee = new Employee();
        employee.setId(1L);

        AiInsightResponse response = new AiInsightResponse(
                1L,
                0.15,
                "LOW",
                "Maintain engagement with recognition and progression checkpoints."
        );

        when(employeeService.findById(1L)).thenReturn(employee);
        when(aiInsightService.estimateAttrition(any(Employee.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/ai/attrition/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.riskBand").value("LOW"))
                .andExpect(jsonPath("$.attritionRisk").value(0.15))
                .andExpect(jsonPath("$.recommendation").exists());
    }

    @Test
    @WithMockUser(roles = "HR")
    void attrition_highRiskEmployee_returnsHighBand() throws Exception {
        Employee employee = new Employee();
        employee.setId(2L);

        AiInsightResponse response = new AiInsightResponse(
                2L,
                0.95,
                "HIGH",
                "Schedule manager 1:1, compensation calibration, and targeted retention plan."
        );

        when(employeeService.findById(2L)).thenReturn(employee);
        when(aiInsightService.estimateAttrition(any(Employee.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/ai/attrition/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(2))
                .andExpect(jsonPath("$.riskBand").value("HIGH"))
                .andExpect(jsonPath("$.attritionRisk").value(0.95));
    }

    // ============================================================
    // SKILL GAP
    // ============================================================

    @Test
    @WithMockUser(roles = "HR")
    void skillGap_returnsSkillGapAnalysis() throws Exception {
        Map<String, Object> response = Map.of(
                "employeeId", 1L,
                "employeeName", "Test Employee",
                "averagePerformance", 55.0,
                "goalCompletionRate", 50.0,
                "averageFeedbackRating", 3.0,
                "skillGaps", List.of(
                        "Performance improvement",
                        "Goal execution and delivery"
                ),
                "priority", "HIGH"
        );

        when(aiInsightService.skillGap(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/ai/skill-gap/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.employeeName").value("Test Employee"))
                .andExpect(jsonPath("$.averagePerformance").value(55.0))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.skillGaps").isArray());
    }

    // ============================================================
    // ENGAGEMENT
    // ============================================================

    @Test
    @WithMockUser(roles = "HR")
    void engagement_returnsEngagementAnalysis() throws Exception {
        Map<String, Object> response = Map.of(
                "employeeId", 1L,
                "employeeName", "Test Employee",
                "engagementScore", 93.5,
                "engagementLevel", "HIGH",
                "attendanceScore", 100.0,
                "workTimeScore", 100.0,
                "performanceScore", 90.0,
                "feedbackScore", 90.0,
                "leaveSignal", 70.0
        );

        when(aiInsightService.engagement(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/ai/engagement/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.engagementScore").value(93.5))
                .andExpect(jsonPath("$.engagementLevel").value("HIGH"))
                .andExpect(jsonPath("$.attendanceScore").value(100.0));
    }

    // ============================================================
    // RECOMMENDATIONS
    // ============================================================

    @Test
    @WithMockUser(roles = "HR")
    void recommendations_returnsWorkforceRecommendations() throws Exception {
        Map<String, Object> response = Map.of(
                "employeeId", 1L,
                "employeeName", "Test Employee",
                "priority", "HIGH",
                "riskBand", "MEDIUM",
                "attritionRisk", 0.60,
                "engagement", Map.of(
                        "engagementScore", 45.0,
                        "engagementLevel", "LOW"
                ),
                "skillGap", Map.of(
                        "priority", "HIGH"
                ),
                "actions", List.of(
                        "Schedule career progression and compensation discussion.",
                        "Create a targeted training and upskilling plan."
                ),
                "aiRecommendation", "Create a targeted retention and development plan."
        );

        when(aiInsightService.recommendations(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/ai/recommendations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.riskBand").value("MEDIUM"))
                .andExpect(jsonPath("$.actions").isArray())
                .andExpect(jsonPath("$.aiRecommendation").exists());
    }

    // ============================================================
    // WORKFORCE DASHBOARD
    // ============================================================

    @Test
    @WithMockUser(roles = "HR")
    void workforceDashboard_returnsAggregatedInsights() throws Exception {
        Map<String, Object> response = Map.of(
                "totalEmployees", 2,
                "activeEmployees", 1L,
                "inactiveEmployees", 1L,
                "highAttritionRisk", 1L,
                "mediumAttritionRisk", 0L,
                "lowAttritionRisk", 1L,
                "averageEngagementScore", 72.5,
                "departmentDistribution", Map.of(
                        "Engineering", 1,
                        "Finance", 1
                ),
                "employeeInsights", List.of()
        );

        when(aiInsightService.workforceDashboard()).thenReturn(response);

        mockMvc.perform(get("/api/v1/ai/workforce-dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(2))
                .andExpect(jsonPath("$.activeEmployees").value(1))
                .andExpect(jsonPath("$.inactiveEmployees").value(1))
                .andExpect(jsonPath("$.highAttritionRisk").value(1))
                .andExpect(jsonPath("$.lowAttritionRisk").value(1))
                .andExpect(jsonPath("$.averageEngagementScore").value(72.5))
                .andExpect(jsonPath("$.departmentDistribution.Engineering").value(1))
                .andExpect(jsonPath("$.employeeInsights").isArray());
    }

    // ============================================================
    // AUTHORIZATION
    // ============================================================

    @Test
    void attrition_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/attrition/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void skillGap_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/skill-gap/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void engagement_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/engagement/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void recommendations_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/recommendations/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void workforceDashboard_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/workforce-dashboard"))
                .andExpect(status().is4xxClientError());
    }
}
