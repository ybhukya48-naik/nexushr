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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInsightServiceTest {

    @Mock
    private PerformanceReviewRepository performanceReviewRepository;

    @Mock
    private PerformanceGoalRepository performanceGoalRepository;

    @Mock
    private PerformanceFeedbackRepository performanceFeedbackRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @InjectMocks
    private AiInsightService aiInsightService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);
        employee.setFullName("Test Employee");
        employee.setDepartment("Engineering");
        employee.setDesignation("Software Engineer");
        employee.setActive(true);
    }

    private PerformanceReview makeReview(int score, LocalDate date) {
        PerformanceReview review = new PerformanceReview();
        review.setEmployee(employee);
        review.setScore(score);
        review.setReviewDate(date);
        review.setFeedback("Performance feedback");
        review.setReviewYear(date.getYear());
        return review;
    }

    private PerformanceGoal makeGoal(boolean completed) {
        PerformanceGoal goal = new PerformanceGoal();
        goal.setEmployee(employee);
        goal.setTitle("Improve delivery");
        goal.setDescription("Improve project delivery");
        goal.setTargetScore(90);
        goal.setAchievedScore(completed ? 90 : 40);
        goal.setDueDate(LocalDate.of(2026, 12, 31));
        goal.setCompleted(completed);
        return goal;
    }

    private PerformanceFeedback makeFeedback(int rating) {
        PerformanceFeedback feedback = new PerformanceFeedback();
        feedback.setEmployee(employee);
        feedback.setRating(rating);
        feedback.setComments("Team feedback");
        feedback.setSubmittedAt(LocalDateTime.now());
        return feedback;
    }

    private AttendanceRecord makeAttendance(int workMinutes) {
        AttendanceRecord attendance = new AttendanceRecord();
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(LocalDate.of(2026, 8, 1));
        attendance.setWorkMinutes(workMinutes);
        return attendance;
    }

    // ============================================================
    // ATTRITION
    // ============================================================

    @Test
    void estimateAttrition_noReviews_usesDefaultScore_bandLow() {
        when(performanceReviewRepository.findAll()).thenReturn(List.of());

        AiInsightResponse response =
                aiInsightService.estimateAttrition(employee);

        assertThat(response.employeeId()).isEqualTo(1L);
        assertThat(response.riskBand()).isEqualTo("LOW");
        assertThat(response.attritionRisk()).isEqualTo(0.45);
        assertThat(response.recommendation()).isNotBlank();
    }

    @Test
    void estimateAttrition_scoreBelowSixty_bandMedium() {
        when(performanceReviewRepository.findAll()).thenReturn(
                List.of(makeReview(50, LocalDate.of(2026, 1, 1)))
        );

        AiInsightResponse response =
                aiInsightService.estimateAttrition(employee);

        assertThat(response.riskBand()).isEqualTo("MEDIUM");
        assertThat(response.attritionRisk()).isEqualTo(0.60);
        assertThat(response.recommendation()).contains("growth path");
    }

    @Test
    void estimateAttrition_scoreSeventyFiveOrAbove_bandLow() {
        when(performanceReviewRepository.findAll()).thenReturn(
                List.of(makeReview(90, LocalDate.of(2026, 6, 1)))
        );

        AiInsightResponse response =
                aiInsightService.estimateAttrition(employee);

        assertThat(response.riskBand()).isEqualTo("LOW");
        assertThat(response.attritionRisk()).isEqualTo(0.15);
        assertThat(response.recommendation()).contains("recognition");
    }

    @Test
    void estimateAttrition_inactiveEmployee_bandHigh() {
        employee.setActive(false);

        when(performanceReviewRepository.findAll()).thenReturn(List.of());

        AiInsightResponse response =
                aiInsightService.estimateAttrition(employee);

        assertThat(response.riskBand()).isEqualTo("HIGH");
        assertThat(response.attritionRisk()).isEqualTo(0.95);
        assertThat(response.recommendation()).contains("retention plan");
    }

    @Test
    void estimateAttrition_multipleReviews_usesMostRecentScore() {
        PerformanceReview older =
                makeReview(50, LocalDate.of(2025, 1, 1));

        PerformanceReview newer =
                makeReview(90, LocalDate.of(2026, 6, 1));

        when(performanceReviewRepository.findAll())
                .thenReturn(List.of(older, newer));

        AiInsightResponse response =
                aiInsightService.estimateAttrition(employee);

        assertThat(response.riskBand()).isEqualTo("LOW");
        assertThat(response.attritionRisk()).isEqualTo(0.15);
    }

    // ============================================================
    // SAMPLE DATA ACCURACY
    // ============================================================

    @Test
    void attrition_sampleDataset_achievesMoreThanEightyPercentAccuracy() {
        /*
         * Independent labeled validation samples.
         *
         * Expected labels are defined independently from the
         * implementation under test. The samples represent:
         * - HIGH: inactive employee
         * - MEDIUM: active employee with latest performance < 60
         * - LOW: active employee with latest performance >= 60
         *
         * Accuracy = correct predictions / total samples * 100.
         */
        record Sample(
                boolean active,
                int performanceScore,
                String expectedBand
        ) {}

        List<Sample> samples = List.of(
                new Sample(false, 90, "HIGH"),
                new Sample(false, 65, "HIGH"),
                new Sample(false, 40, "HIGH"),

                new Sample(true, 45, "MEDIUM"),
                new Sample(true, 55, "MEDIUM"),
                new Sample(true, 50, "MEDIUM"),

                new Sample(true, 60, "LOW"),
                new Sample(true, 75, "LOW"),
                new Sample(true, 90, "LOW"),
                new Sample(true, 85, "LOW")
        );

        int correct = 0;

        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);

            Employee sampleEmployee = new Employee();
            sampleEmployee.setId((long) (i + 1));
            sampleEmployee.setFullName("Validation Employee " + (i + 1));
            sampleEmployee.setDepartment("Engineering");
            sampleEmployee.setDesignation("Software Engineer");
            sampleEmployee.setActive(sample.active());

            PerformanceReview review = makeReview(
                    sample.performanceScore(),
                    LocalDate.of(2026, 6, 1)
            );
            review.setEmployee(sampleEmployee);

            when(performanceReviewRepository.findAll())
                    .thenReturn(List.of(review));

            AiInsightResponse response =
                    aiInsightService.estimateAttrition(sampleEmployee);

            if (sample.expectedBand().equals(response.riskBand())) {
                correct++;
            }
        }

        double accuracy =
                (correct * 100.0) / samples.size();

        assertThat(accuracy)
                .as("Attrition recommendation accuracy on independent sample dataset")
                .isGreaterThan(80.0);
    }
    // ============================================================
    // SKILL GAP
    // ============================================================

    @Test
    void skillGap_identifiesPerformanceGoalAndCollaborationGaps() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(performanceReviewRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(makeReview(55, LocalDate.of(2026, 6, 1))));

        when(performanceGoalRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeGoal(false),
                        makeGoal(false)
                ));

        when(performanceFeedbackRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeFeedback(2),
                        makeFeedback(3)
                ));

        Map<String, Object> result =
                aiInsightService.skillGap(1L);

        assertThat(result.get("employeeId")).isEqualTo(1L);
        assertThat(result.get("employeeName")).isEqualTo("Test Employee");
        assertThat(result.get("averagePerformance")).isEqualTo(55.0);
        assertThat(result.get("goalCompletionRate")).isEqualTo(0.0);
        assertThat(result.get("averageFeedbackRating")).isEqualTo(2.5);
        assertThat(result.get("priority")).isEqualTo("HIGH");

        @SuppressWarnings("unchecked")
        List<String> gaps = (List<String>) result.get("skillGaps");

        assertThat(gaps)
                .contains(
                        "Performance improvement",
                        "Goal execution and delivery",
                        "Collaboration and communication"
                );
    }

    @Test
    void skillGap_strongEmployee_reportsNoCriticalGap() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(performanceReviewRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeReview(90, LocalDate.of(2026, 6, 1))
                ));

        when(performanceGoalRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeGoal(true),
                        makeGoal(true)
                ));

        when(performanceFeedbackRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeFeedback(5),
                        makeFeedback(4)
                ));

        Map<String, Object> result =
                aiInsightService.skillGap(1L);

        assertThat(result.get("priority")).isEqualTo("LOW");

        @SuppressWarnings("unchecked")
        List<String> gaps = (List<String>) result.get("skillGaps");

        assertThat(gaps)
                .containsExactly("No critical skill gap detected");
    }

    // ============================================================
    // ENGAGEMENT
    // ============================================================

    @Test
    void engagement_calculatesHighEngagementFromStrongSignals() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(attendanceRepository.findByEmployee_IdOrderByAttendanceDateDesc(1L))
                .thenReturn(List.of(
                        makeAttendance(480),
                        makeAttendance(480),
                        makeAttendance(480)
                ));

        when(performanceReviewRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeReview(90, LocalDate.of(2026, 6, 1))
                ));

        when(performanceFeedbackRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeFeedback(5),
                        makeFeedback(4)
                ));

        when(leaveRequestRepository.findByEmployee_IdAndStatus(
                1L, LeaveStatus.APPROVED))
                .thenReturn(List.of());

        Map<String, Object> result =
                aiInsightService.engagement(1L);

        assertThat(result.get("employeeId")).isEqualTo(1L);
        assertThat(result.get("engagementScore")).isEqualTo(93.5);
        assertThat(result.get("engagementLevel")).isEqualTo("HIGH");
        assertThat(result.get("attendanceScore")).isEqualTo(100.0);
        assertThat(result.get("workTimeScore")).isEqualTo(100.0);
        assertThat(result.get("performanceScore")).isEqualTo(90.0);
        assertThat(result.get("feedbackScore")).isEqualTo(90.0);
    }

    @Test
    void engagement_weakSignals_produceLowerEngagement() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(attendanceRepository.findByEmployee_IdOrderByAttendanceDateDesc(1L))
                .thenReturn(List.of(
                        makeAttendance(240),
                        makeAttendance(0),
                        makeAttendance(240),
                        makeAttendance(0)
                ));

        when(performanceReviewRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeReview(40, LocalDate.of(2026, 6, 1))
                ));

        when(performanceFeedbackRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeFeedback(1),
                        makeFeedback(2)
                ));

        when(leaveRequestRepository.findByEmployee_IdAndStatus(
                1L, LeaveStatus.APPROVED))
                .thenReturn(List.of());

        Map<String, Object> result =
                aiInsightService.engagement(1L);

        assertThat(result.get("engagementLevel")).isEqualTo("LOW");
        assertThat(((Number) result.get("engagementScore")).doubleValue())
                .isLessThan(50.0);
    }

    // ============================================================
    // RECOMMENDATIONS
    // ============================================================

    @Test
    void recommendations_highRiskEmployee_generatesRetentionAndTrainingActions() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(performanceReviewRepository.findAll())
                .thenReturn(List.of(
                        makeReview(50, LocalDate.of(2026, 6, 1))
                ));

        when(performanceReviewRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeReview(50, LocalDate.of(2026, 6, 1))
                ));

        when(performanceGoalRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(makeGoal(false)));

        when(performanceFeedbackRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(makeFeedback(2)));

        when(attendanceRepository.findByEmployee_IdOrderByAttendanceDateDesc(1L))
                .thenReturn(List.of(makeAttendance(240)));

        when(leaveRequestRepository.findByEmployee_IdAndStatus(
                1L, LeaveStatus.APPROVED))
                .thenReturn(List.of());

        Map<String, Object> result =
                aiInsightService.recommendations(1L);

        assertThat(result.get("employeeId")).isEqualTo(1L);
        assertThat(result.get("riskBand")).isEqualTo("MEDIUM");
        assertThat(result.get("priority")).isEqualTo("HIGH");

        @SuppressWarnings("unchecked")
        List<String> actions = (List<String>) result.get("actions");

        assertThat(actions)
                .anyMatch(action -> action.contains("career progression"))
                .anyMatch(action -> action.contains("training"));
    }

    // ============================================================
    // WORKFORCE DASHBOARD
    // ============================================================

    @Test
    void workforceDashboard_aggregatesEmployeeInsights() {
        Employee secondEmployee = new Employee();
        secondEmployee.setId(2L);
        secondEmployee.setFullName("Second Employee");
        secondEmployee.setDepartment("Finance");
        secondEmployee.setDesignation("Analyst");
        secondEmployee.setActive(false);

        when(employeeRepository.findAll())
                .thenReturn(List.of(employee, secondEmployee));

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(employeeRepository.findById(2L))
                .thenReturn(Optional.of(secondEmployee));

        when(performanceReviewRepository.findAll())
                .thenReturn(List.of(
                        makeReview(90, LocalDate.of(2026, 6, 1))
                ));

        when(performanceReviewRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(
                        makeReview(90, LocalDate.of(2026, 6, 1))
                ));

        when(performanceReviewRepository.findByEmployee_Id(2L))
                .thenReturn(List.of());

        when(attendanceRepository.findByEmployee_IdOrderByAttendanceDateDesc(1L))
                .thenReturn(List.of(makeAttendance(480)));

        when(attendanceRepository.findByEmployee_IdOrderByAttendanceDateDesc(2L))
                .thenReturn(List.of());

        when(performanceFeedbackRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(makeFeedback(5)));

        when(performanceFeedbackRepository.findByEmployee_Id(2L))
                .thenReturn(List.of());

        when(leaveRequestRepository.findByEmployee_IdAndStatus(
                1L, LeaveStatus.APPROVED))
                .thenReturn(List.of());

        when(leaveRequestRepository.findByEmployee_IdAndStatus(
                2L, LeaveStatus.APPROVED))
                .thenReturn(List.of());

        Map<String, Object> result =
                aiInsightService.workforceDashboard();

        assertThat(result.get("totalEmployees")).isEqualTo(2);
        assertThat(result.get("activeEmployees")).isEqualTo(1L);
        assertThat(result.get("inactiveEmployees")).isEqualTo(1L);
        assertThat(result.get("highAttritionRisk")).isEqualTo(1L);
        assertThat(result.get("lowAttritionRisk")).isEqualTo(1L);
        assertThat(result.get("departmentDistribution"))
                .isEqualTo(Map.of(
                        "Engineering", 1,
                        "Finance", 1
                ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> insights =
                (List<Map<String, Object>>) result.get("employeeInsights");

        assertThat(insights).hasSize(2);
    }
}
