package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.PerformanceFeedback;
import com.zidio.nexushr.domain.PerformanceGoal;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.security.AuthorizationService;
import com.zidio.nexushr.service.PerformanceFeedbackService;
import com.zidio.nexushr.service.PerformanceGoalService;
import com.zidio.nexushr.service.PerformanceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    private final PerformanceService performanceService;
    private final PerformanceGoalService performanceGoalService;
    private final PerformanceFeedbackService performanceFeedbackService;
    private final AuthorizationService authorizationService;

    public PerformanceController(
            PerformanceService performanceService,
            PerformanceGoalService performanceGoalService,
            PerformanceFeedbackService performanceFeedbackService,
            AuthorizationService authorizationService) {
        this.performanceService = performanceService;
        this.performanceGoalService = performanceGoalService;
        this.performanceFeedbackService = performanceFeedbackService;
        this.authorizationService = authorizationService;
    }

    // =========================================================
    // PERFORMANCE REVIEWS
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public List<PerformanceReview> list(Authentication authentication) {
        return performanceService.findAll().stream()
                .filter(review -> review.getEmployee() == null
                        || authorizationService.canSeeEmployee(review.getEmployee(), authentication))
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public PerformanceReview create(
            @RequestBody PerformanceReview review,
            Authentication authentication) {
        requireCanSeeEmployeeId(review == null || review.getEmployee() == null
                ? null
                : review.getEmployee().getId(), authentication);
        return performanceService.create(review);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public List<PerformanceReview> listByEmployee(
            @PathVariable Long employeeId) {
        return performanceService.findByEmployee(employeeId);
    }

    // =========================================================
    // PERFORMANCE SCORECARD
    // =========================================================

    @GetMapping("/scorecard/{employeeId}")
    @PreAuthorize("@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public Map<String, Object> scorecard(
            @PathVariable Long employeeId) {
        return performanceService.getScorecard(employeeId);
    }

    // =========================================================
    // COMPLETE PERFORMANCE DASHBOARD
    // =========================================================

    @GetMapping("/dashboard/{employeeId}")
    @PreAuthorize("@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
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
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public List<PerformanceGoal> listGoals(Authentication authentication) {
        return performanceGoalService.findAll().stream()
                .filter(goal -> goal.getEmployee() == null
                        || authorizationService.canSeeEmployee(goal.getEmployee(), authentication))
                .toList();
    }

    @GetMapping("/goals/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public List<PerformanceGoal> listGoalsByEmployee(
            @PathVariable Long employeeId) {
        return performanceGoalService.findByEmployee(employeeId);
    }

    @PostMapping("/goals")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public PerformanceGoal createGoal(
            @RequestBody PerformanceGoal goal,
            Authentication authentication) {
        requireCanSeeEmployeeId(goal == null || goal.getEmployee() == null
                ? null
                : goal.getEmployee().getId(), authentication);
        return performanceGoalService.create(goal);
    }

    @PatchMapping("/goals/{goalId}/progress")
    @PreAuthorize("@authorizationService.canAccessGoal(#goalId, authentication)")
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
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public List<PerformanceFeedback> listFeedback(Authentication authentication) {
        return performanceFeedbackService.findAll().stream()
                .filter(feedback -> feedback.getEmployee() == null
                        || authorizationService.canSeeEmployee(feedback.getEmployee(), authentication))
                .toList();
    }

    @PostMapping("/feedback")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public PerformanceFeedback createFeedback(
            @RequestBody PerformanceFeedback feedback,
            Authentication authentication) {
        requireCanSeeEmployeeId(feedback == null || feedback.getEmployee() == null
                ? null
                : feedback.getEmployee().getId(), authentication);
        return performanceFeedbackService.create(feedback);
    }

    @GetMapping("/feedback/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public List<PerformanceFeedback> listFeedbackByEmployee(
            @PathVariable Long employeeId) {
        return performanceFeedbackService.findByEmployee(employeeId);
    }

    @GetMapping("/feedback/reviewer/{reviewerId}")
    @PreAuthorize("@authorizationService.canAccessReviewer(#reviewerId, authentication)")
    public List<PerformanceFeedback> listFeedbackByReviewer(
            @PathVariable Long reviewerId) {
        return performanceFeedbackService.findByReviewer(reviewerId);
    }

    private void requireCanSeeEmployeeId(Long employeeId, Authentication authentication) {
        if (!authorizationService.canSeeEmployeeId(employeeId, authentication)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
        }
    }
}
