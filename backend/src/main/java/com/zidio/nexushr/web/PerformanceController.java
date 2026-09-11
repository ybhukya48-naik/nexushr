package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.PerformanceFeedback;
import com.zidio.nexushr.domain.PerformanceGoal;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.service.PerformanceFeedbackService;
import com.zidio.nexushr.service.PerformanceGoalService;
import com.zidio.nexushr.service.PerformanceService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    private final PerformanceService performanceService;
    private final PerformanceGoalService performanceGoalService;
    private final PerformanceFeedbackService performanceFeedbackService;

    public PerformanceController(
            PerformanceService performanceService,
            PerformanceGoalService performanceGoalService,
            PerformanceFeedbackService performanceFeedbackService) {
        this.performanceService = performanceService;
        this.performanceGoalService = performanceGoalService;
        this.performanceFeedbackService = performanceFeedbackService;
    }

    // =========================================================
    // PERFORMANCE REVIEWS
    // =========================================================

    @GetMapping
    public List<PerformanceReview> list() {
        return performanceService.findAll();
    }

    @PostMapping
    public PerformanceReview create(
            @RequestBody PerformanceReview review) {
        return performanceService.create(review);
    }

    @GetMapping("/employee/{employeeId}")
    public List<PerformanceReview> listByEmployee(
            @PathVariable Long employeeId) {
        return performanceService.findByEmployee(employeeId);
    }

    // =========================================================
    // PERFORMANCE SCORECARD
    // =========================================================

    @GetMapping("/scorecard/{employeeId}")
    public Map<String, Object> scorecard(
            @PathVariable Long employeeId) {
        return performanceService.getScorecard(employeeId);
    }

    // =========================================================
    // COMPLETE PERFORMANCE DASHBOARD
    // =========================================================

    @GetMapping("/dashboard/{employeeId}")
    public Map<String, Object> dashboard(
            @PathVariable Long employeeId) {

        Map<String, Object> dashboard = new LinkedHashMap<>();

        dashboard.put("employeeId", employeeId);
        dashboard.put(
                "scorecard",
                performanceService.getScorecard(employeeId)
        );
        dashboard.put(
                "reviews",
                performanceService.findByEmployee(employeeId)
        );
        dashboard.put(
                "goals",
                performanceGoalService.findByEmployee(employeeId)
        );
        dashboard.put(
                "feedback",
                performanceFeedbackService.findByEmployee(employeeId)
        );

        return dashboard;
    }

    // =========================================================
    // PERFORMANCE GOALS
    // =========================================================

    @GetMapping("/goals")
    public List<PerformanceGoal> listGoals() {
        return performanceGoalService.findAll();
    }

    @GetMapping("/goals/employee/{employeeId}")
    public List<PerformanceGoal> listGoalsByEmployee(
            @PathVariable Long employeeId) {
        return performanceGoalService.findByEmployee(employeeId);
    }

    @PostMapping("/goals")
    public PerformanceGoal createGoal(
            @RequestBody PerformanceGoal goal) {
        return performanceGoalService.create(goal);
    }

    @PatchMapping("/goals/{goalId}/progress")
    public PerformanceGoal updateGoalProgress(
            @PathVariable Long goalId,
            @RequestParam Integer achievedScore,
            @RequestParam Boolean completed) {
        return performanceGoalService.updateProgress(
                goalId,
                achievedScore,
                completed
        );
    }

    // =========================================================
    // 360-DEGREE FEEDBACK
    // =========================================================

    @GetMapping("/feedback")
    public List<PerformanceFeedback> listFeedback() {
        return performanceFeedbackService.findAll();
    }

    @PostMapping("/feedback")
    public PerformanceFeedback createFeedback(
            @RequestBody PerformanceFeedback feedback) {
        return performanceFeedbackService.create(feedback);
    }

    @GetMapping("/feedback/employee/{employeeId}")
    public List<PerformanceFeedback> listFeedbackByEmployee(
            @PathVariable Long employeeId) {
        return performanceFeedbackService.findByEmployee(employeeId);
    }

    @GetMapping("/feedback/reviewer/{reviewerId}")
    public List<PerformanceFeedback> listFeedbackByReviewer(
            @PathVariable Long reviewerId) {
        return performanceFeedbackService.findByReviewer(reviewerId);
    }
}
