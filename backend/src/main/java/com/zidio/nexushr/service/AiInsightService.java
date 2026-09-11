package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.domain.PerformanceFeedback;
import com.zidio.nexushr.domain.PerformanceGoal;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.LeaveRequestRepository;
import com.zidio.nexushr.repository.PerformanceFeedbackRepository;
import com.zidio.nexushr.repository.PerformanceGoalRepository;
import com.zidio.nexushr.repository.PerformanceReviewRepository;
import com.zidio.nexushr.web.dto.AiInsightResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiInsightService {

    private final PerformanceReviewRepository performanceReviewRepository;
    private final PerformanceGoalRepository performanceGoalRepository;
    private final PerformanceFeedbackRepository performanceFeedbackRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final ChatClient chatClient;

    public AiInsightService(
            PerformanceReviewRepository performanceReviewRepository,
            PerformanceGoalRepository performanceGoalRepository,
            PerformanceFeedbackRepository performanceFeedbackRepository,
            AttendanceRepository attendanceRepository,
            LeaveRequestRepository leaveRequestRepository,
            EmployeeRepository employeeRepository,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {

        this.performanceReviewRepository = performanceReviewRepository;
        this.performanceGoalRepository = performanceGoalRepository;
        this.performanceFeedbackRepository = performanceFeedbackRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        ChatClient.Builder chatClientBuilder = chatClientBuilderProvider != null ? chatClientBuilderProvider.getIfAvailable() : null;
        this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
    }

    /**
     * Existing attrition model.
     * Kept compatible with the existing AI integration tests.
     */
    public AiInsightResponse estimateAttrition(Employee employee) {

        List<PerformanceReview> reviews = performanceReviewRepository.findAll().stream()
                .filter(r -> r.getEmployee() != null
                        && r.getEmployee().getId() != null
                        && r.getEmployee().getId().equals(employee.getId()))
                .sorted(Comparator.comparing(
                        PerformanceReview::getReviewDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int latestScore = reviews.isEmpty() ? 65 : reviews.get(0).getScore();

        double baseRisk = 0.25;

        if (latestScore < 60) {
            baseRisk += 0.35;
        } else if (latestScore < 75) {
            baseRisk += 0.2;
        } else {
            baseRisk -= 0.1;
        }

        if (!employee.isActive()) {
            baseRisk = 1.0;
        }

        double normalized = Math.max(0.05, Math.min(0.95, baseRisk));

        String band = normalized > 0.7
                ? "HIGH"
                : normalized > 0.45
                ? "MEDIUM"
                : "LOW";

        String recommendation;

        try {
            String prompt = String.format("""
                    You are an HR analytics expert. Based on the following employee data, provide a concise (max 2 sentences) retention recommendation:
                    - Employee Name: %s
                    - Role: %s
                    - Department: %s
                    - Latest Performance Score: %d/100
                    - Attrition Risk Band: %s
                    """,
                    employee.getFullName(),
                    employee.getRoleType(),
                    employee.getDepartment(),
                    latestScore,
                    band);

            if (chatClient == null) {
                throw new IllegalStateException("AI chat client is not configured");
            }

            recommendation = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (recommendation == null || recommendation.isBlank()) {
                throw new IllegalStateException("Empty AI response");
            }

        } catch (Exception e) {
            recommendation = switch (band) {
                case "HIGH" ->
                        "Schedule manager 1:1, compensation calibration, and targeted retention plan.";
                case "MEDIUM" ->
                        "Review growth path and assign upskilling roadmap.";
                default ->
                        "Maintain engagement with recognition and progression checkpoints.";
            };
        }

        return new AiInsightResponse(
                employee.getId(),
                normalized,
                band,
                recommendation
        );
    }

    /**
     * Skill-gap analysis based on the employee's existing performance data.
     *
     * Since the current domain does not contain a dedicated skills table,
     * development gaps are inferred from performance reviews, goals and feedback.
     */
    public Map<String, Object> skillGap(Long employeeId) {

        Employee employee = findEmployee(employeeId);

        List<PerformanceReview> reviews =
                performanceReviewRepository.findByEmployee_Id(employeeId);

        List<PerformanceGoal> goals =
                performanceGoalRepository.findByEmployee_Id(employeeId);

        List<PerformanceFeedback> feedback =
                performanceFeedbackRepository.findByEmployee_Id(employeeId);

        double averagePerformance = reviews.stream()
                .filter(r -> r.getScore() != null)
                .mapToInt(PerformanceReview::getScore)
                .average()
                .orElse(65.0);

        double goalCompletion = goals.isEmpty()
                ? 100.0
                : goals.stream()
                    .filter(PerformanceGoal::isCompleted)
                    .count() * 100.0 / goals.size();

        double feedbackRating = feedback.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(PerformanceFeedback::getRating)
                .average()
                .orElse(3.0);

        List<String> skillGaps = new ArrayList<>();

        if (averagePerformance < 70) {
            skillGaps.add("Performance improvement");
        }

        if (goalCompletion < 70) {
            skillGaps.add("Goal execution and delivery");
        }

        if (feedbackRating < 3.5) {
            skillGaps.add("Collaboration and communication");
        }

        if (skillGaps.isEmpty()) {
            skillGaps.add("No critical skill gap detected");
        }

        String priority = averagePerformance < 60 || goalCompletion < 50
                ? "HIGH"
                : averagePerformance < 75 || feedbackRating < 3.5
                ? "MEDIUM"
                : "LOW";

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("employeeId", employee.getId());
        result.put("employeeName", employee.getFullName());
        result.put("department", employee.getDepartment());
        result.put("designation", employee.getDesignation());
        result.put("averagePerformance", round(averagePerformance));
        result.put("goalCompletionRate", round(goalCompletion));
        result.put("averageFeedbackRating", round(feedbackRating));
        result.put("skillGaps", skillGaps);
        result.put("priority", priority);

        return result;
    }

    /**
     * Employee engagement score from 0-100.
     *
     * Signals:
     * - attendance frequency
     * - average working time
     * - performance
     * - feedback
     * - approved leave activity
     */
    public Map<String, Object> engagement(Long employeeId) {

        Employee employee = findEmployee(employeeId);

        List<AttendanceRecord> attendance =
                attendanceRepository.findByEmployee_IdOrderByAttendanceDateDesc(employeeId);

        List<PerformanceReview> reviews =
                performanceReviewRepository.findByEmployee_Id(employeeId);

        List<PerformanceFeedback> feedback =
                performanceFeedbackRepository.findByEmployee_Id(employeeId);

        List<LeaveRequest> approvedLeave =
                leaveRequestRepository.findByEmployee_IdAndStatus(
                        employeeId,
                        LeaveStatus.APPROVED
                );

        double attendanceScore;

        if (attendance.isEmpty()) {
            attendanceScore = 70.0;
        } else {
            long daysWithWork = attendance.stream()
                    .filter(a -> a.getWorkMinutes() != null && a.getWorkMinutes() > 0)
                    .count();

            attendanceScore = Math.min(
                    100.0,
                    50.0 + (daysWithWork * 50.0 / attendance.size())
            );
        }

        double workTimeScore;

        if (attendance.isEmpty()) {
            workTimeScore = 70.0;
        } else {
            double averageMinutes = attendance.stream()
                    .filter(a -> a.getWorkMinutes() != null)
                    .mapToInt(AttendanceRecord::getWorkMinutes)
                    .average()
                    .orElse(0.0);

            workTimeScore = Math.min(
                    100.0,
                    Math.max(0.0, averageMinutes / 480.0 * 100.0)
            );
        }

        double performanceScore = reviews.stream()
                .filter(r -> r.getScore() != null)
                .mapToInt(PerformanceReview::getScore)
                .average()
                .orElse(65.0);

        double feedbackScore = feedback.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(PerformanceFeedback::getRating)
                .average()
                .orElse(3.0) * 20.0;

        /*
         * Leave is treated as a neutral activity signal rather than a
         * penalty. Approved leave demonstrates that employees are using
         * the available leave workflow.
         */
        double leaveSignal = approvedLeave.isEmpty() ? 70.0 : 80.0;

        double engagementScore =
                attendanceScore * 0.25
                + workTimeScore * 0.20
                + performanceScore * 0.30
                + feedbackScore * 0.20
                + leaveSignal * 0.05;

        engagementScore = Math.max(0.0, Math.min(100.0, engagementScore));

        String level = engagementScore >= 75
                ? "HIGH"
                : engagementScore >= 50
                ? "MEDIUM"
                : "LOW";

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("employeeId", employee.getId());
        result.put("employeeName", employee.getFullName());
        result.put("engagementScore", round(engagementScore));
        result.put("engagementLevel", level);
        result.put("attendanceScore", round(attendanceScore));
        result.put("workTimeScore", round(workTimeScore));
        result.put("performanceScore", round(performanceScore));
        result.put("feedbackScore", round(feedbackScore));
        result.put("approvedLeaveRequests", approvedLeave.size());

        return result;
    }

    /**
     * Consolidated AI/workforce recommendation.
     */
    public Map<String, Object> recommendations(Long employeeId) {

        Employee employee = findEmployee(employeeId);

        AiInsightResponse attrition = estimateAttrition(employee);
        Map<String, Object> skillGap = skillGap(employeeId);
        Map<String, Object> engagement = engagement(employeeId);

        String engagementLevel =
                String.valueOf(engagement.get("engagementLevel"));

        String priority;

        if ("HIGH".equals(attrition.riskBand())
                || "LOW".equals(engagementLevel)
                || "HIGH".equals(skillGap.get("priority"))) {
            priority = "HIGH";
        } else if ("MEDIUM".equals(attrition.riskBand())
                || "MEDIUM".equals(engagementLevel)
                || "MEDIUM".equals(skillGap.get("priority"))) {
            priority = "MEDIUM";
        } else {
            priority = "LOW";
        }

        List<String> actions = new ArrayList<>();

        if ("HIGH".equals(attrition.riskBand())) {
            actions.add("Schedule an immediate manager retention discussion.");
        } else if ("MEDIUM".equals(attrition.riskBand())) {
            actions.add("Review career progression and compensation alignment.");
        }

        if ("LOW".equals(engagementLevel)) {
            actions.add("Create an employee engagement improvement plan.");
        } else if ("MEDIUM".equals(engagementLevel)) {
            actions.add("Increase manager check-ins and recognition activities.");
        }

        @SuppressWarnings("unchecked")
        List<String> gaps =
                (List<String>) skillGap.get("skillGaps");

        if (gaps != null
                && !gaps.isEmpty()
                && !"No critical skill gap detected".equals(gaps.get(0))) {
            actions.add("Assign targeted training for identified skill gaps.");
        }

        if (actions.isEmpty()) {
            actions.add("Maintain current engagement, recognition and development activities.");
        }

        String aiRecommendation = null;

        try {
            if (chatClient != null) {
                String prompt = String.format("""
                        You are an enterprise HR workforce intelligence assistant.
                        Give one concise recommendation for this employee.

                        Employee: %s
                        Department: %s
                        Attrition Risk: %s
                        Engagement Level: %s
                        Skill Gap Priority: %s

                        Respond in no more than two sentences.
                        """,
                        employee.getFullName(),
                        employee.getDepartment(),
                        attrition.riskBand(),
                        engagementLevel,
                        skillGap.get("priority"));

                aiRecommendation = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                if (aiRecommendation != null && aiRecommendation.isBlank()) {
                    aiRecommendation = null;
                }
            }
        } catch (Exception ignored) {
            // Deterministic recommendation below remains available.
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("employeeId", employeeId);
        result.put("employeeName", employee.getFullName());
        result.put("priority", priority);
        result.put("attritionRisk", attrition.attritionRisk());
        result.put("riskBand", attrition.riskBand());
        result.put("engagement", engagement);
        result.put("skillGap", skillGap);
        result.put("actions", actions);
        result.put(
                "aiRecommendation",
                aiRecommendation != null
                        ? aiRecommendation
                        : String.join(" ", actions)
        );

        return result;
    }

    /**
     * Workforce-wide AI analytics for HR/Admin/Manager dashboards.
     */
    public Map<String, Object> workforceDashboard() {

        List<Employee> employees = employeeRepository.findAll();

        int totalEmployees = employees.size();

        long activeEmployees = employees.stream()
                .filter(Employee::isActive)
                .count();

        long highRiskEmployees = 0;
        long mediumRiskEmployees = 0;
        long lowRiskEmployees = 0;

        double totalEngagement = 0.0;

        Map<String, Integer> departmentCounts = new LinkedHashMap<>();

        List<Map<String, Object>> employeeInsights = new ArrayList<>();

        for (Employee employee : employees) {

            AiInsightResponse attrition = estimateAttrition(employee);
            Map<String, Object> engagement = engagement(employee.getId());

            double engagementScore =
                    ((Number) engagement.get("engagementScore")).doubleValue();

            totalEngagement += engagementScore;

            switch (attrition.riskBand()) {
                case "HIGH" -> highRiskEmployees++;
                case "MEDIUM" -> mediumRiskEmployees++;
                default -> lowRiskEmployees++;
            }

            String department =
                    employee.getDepartment() == null
                            ? "Unassigned"
                            : employee.getDepartment();

            departmentCounts.merge(department, 1, Integer::sum);

            Map<String, Object> insight = new LinkedHashMap<>();

            insight.put("employeeId", employee.getId());
            insight.put("employeeName", employee.getFullName());
            insight.put("department", department);
            insight.put("attritionRisk", attrition.attritionRisk());
            insight.put("riskBand", attrition.riskBand());
            insight.put("engagementScore", engagementScore);

            employeeInsights.add(insight);
        }

        double averageEngagement =
                totalEmployees == 0
                        ? 0.0
                        : totalEngagement / totalEmployees;

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("totalEmployees", totalEmployees);
        result.put("activeEmployees", activeEmployees);
        result.put("inactiveEmployees", totalEmployees - activeEmployees);
        result.put("highAttritionRisk", highRiskEmployees);
        result.put("mediumAttritionRisk", mediumRiskEmployees);
        result.put("lowAttritionRisk", lowRiskEmployees);
        result.put("averageEngagementScore", round(averageEngagement));
        result.put("departmentDistribution", departmentCounts);
        result.put("employeeInsights", employeeInsights);
        result.put("generatedAt", LocalDate.now());

        return result;
    }

    private Employee findEmployee(Long employeeId) {

        if (employeeId == null) {
            throw new IllegalArgumentException("Employee ID is required");
        }

        return employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Employee not found: " + employeeId));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
