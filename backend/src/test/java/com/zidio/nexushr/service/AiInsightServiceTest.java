package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.repository.PerformanceReviewRepository;
import com.zidio.nexushr.web.dto.AiInsightResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInsightServiceTest {

    @Mock
    private PerformanceReviewRepository performanceReviewRepository;

    @InjectMocks
    private AiInsightService aiInsightService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);
        employee.setFullName("Test Employee");
        employee.setActive(true);
    }

    private PerformanceReview makeReview(int score, LocalDate date) {
        PerformanceReview r = new PerformanceReview();
        r.setEmployee(employee);
        r.setScore(score);
        r.setReviewDate(date);
        r.setFeedback("feedback");
        r.setReviewYear(date.getYear());
        return r;
    }

    // --- Empty reviews → default score 65 → band LOW (risk ~0.45) ---

    @Test
    void estimateAttrition_noReviews_usesDefaultScore_bandLow() {
        when(performanceReviewRepository.findAll()).thenReturn(List.of());

        AiInsightResponse response = aiInsightService.estimateAttrition(employee);

        assertThat(response.employeeId()).isEqualTo(1L);
        assertThat(response.riskBand()).isEqualTo("LOW");
        assertThat(response.attritionRisk()).isEqualTo(0.45);
        assertThat(response.recommendation()).isNotBlank();
    }

    // --- Score < 60 → baseRisk 0.60 → MEDIUM ---

    @Test
    void estimateAttrition_scoreBelowSixty_bandMedium() {
        // score 50 < 60 → baseRisk = 0.25 + 0.35 = 0.60
        when(performanceReviewRepository.findAll()).thenReturn(
                List.of(makeReview(50, LocalDate.of(2026, 1, 1)))
        );

        AiInsightResponse response = aiInsightService.estimateAttrition(employee);

        assertThat(response.riskBand()).isEqualTo("MEDIUM");
        assertThat(response.attritionRisk()).isEqualTo(0.60);
        assertThat(response.recommendation()).contains("growth path");
    }

    // --- Score in [60, 75) → baseRisk 0.45 → LOW ---

    @Test
    void estimateAttrition_scoreBetweenSixtyAndSeventyFive_bandLow() {
        // score 70 → baseRisk = 0.25 + 0.20 = 0.45 → not > 0.45 → LOW
        when(performanceReviewRepository.findAll()).thenReturn(
                List.of(makeReview(70, LocalDate.of(2026, 1, 1)))
        );

        AiInsightResponse response = aiInsightService.estimateAttrition(employee);

        assertThat(response.riskBand()).isEqualTo("LOW");
        assertThat(response.attritionRisk()).isEqualTo(0.45);
    }

    // --- Score >= 75 → baseRisk 0.15 → LOW ---

    @Test
    void estimateAttrition_scoreSeventyFiveOrAbove_bandLow_lowRisk() {
        // score 90 → baseRisk = 0.25 - 0.10 = 0.15 → LOW
        when(performanceReviewRepository.findAll()).thenReturn(
                List.of(makeReview(90, LocalDate.of(2026, 1, 1)))
        );

        AiInsightResponse response = aiInsightService.estimateAttrition(employee);

        assertThat(response.riskBand()).isEqualTo("LOW");
        assertThat(response.attritionRisk()).isEqualTo(0.15);
        assertThat(response.recommendation()).contains("recognition");
    }

    // --- Score that produces HIGH band (score < 60 + inactive override check) ---

    @Test
    void estimateAttrition_inactiveEmployee_bandHigh() {
        // inactive employee always maps to risk 1.0 → clamped to 0.95 → HIGH
        employee.setActive(false);
        when(performanceReviewRepository.findAll()).thenReturn(List.of());

        AiInsightResponse response = aiInsightService.estimateAttrition(employee);

        assertThat(response.riskBand()).isEqualTo("HIGH");
        assertThat(response.attritionRisk()).isEqualTo(0.95);
        assertThat(response.recommendation()).contains("retention plan");
    }

    // --- Multiple reviews: most recent (by date) is selected ---

    @Test
    void estimateAttrition_multipleReviews_mostRecentScoreUsed() {
        // Older review has score 50 (would give MEDIUM); newer has score 90 (gives LOW)
        PerformanceReview older = makeReview(50, LocalDate.of(2025, 1, 1));
        PerformanceReview newer = makeReview(90, LocalDate.of(2026, 6, 1));
        when(performanceReviewRepository.findAll()).thenReturn(List.of(older, newer));

        AiInsightResponse response = aiInsightService.estimateAttrition(employee);

        // Should use score 90 (newer), not 50 (older)
        assertThat(response.riskBand()).isEqualTo("LOW");
        assertThat(response.attritionRisk()).isEqualTo(0.15);
    }

    // --- Reviews for a different employee are ignored ---

    @Test
    void estimateAttrition_reviewsForOtherEmployee_ignored() {
        Employee other = new Employee();
        other.setId(99L);
        PerformanceReview otherReview = new PerformanceReview();
        otherReview.setEmployee(other);
        otherReview.setScore(50);
        otherReview.setReviewDate(LocalDate.of(2026, 1, 1));
        otherReview.setFeedback("other");
        otherReview.setReviewYear(2026);

        when(performanceReviewRepository.findAll()).thenReturn(List.of(otherReview));

        // Other employee's reviews are filtered; falls back to default score 65 → LOW
        AiInsightResponse response = aiInsightService.estimateAttrition(employee);

        assertThat(response.riskBand()).isEqualTo("LOW");
        assertThat(response.attritionRisk()).isEqualTo(0.45);
    }

    // --- Risk clamping: baseRisk minimum 0.05 ---

    @Test
    void estimateAttrition_riskNeverBelowMinimum() {
        when(performanceReviewRepository.findAll()).thenReturn(
                List.of(makeReview(100, LocalDate.of(2026, 1, 1)))
        );

        AiInsightResponse response = aiInsightService.estimateAttrition(employee);

        assertThat(response.attritionRisk()).isGreaterThanOrEqualTo(0.05);
    }
}
