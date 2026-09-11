package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PerformanceReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PerformanceService {

    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;

    public PerformanceService(
            PerformanceReviewRepository reviewRepository,
            EmployeeRepository employeeRepository) {
        this.reviewRepository = reviewRepository;
        this.employeeRepository = employeeRepository;
    }

    public PerformanceReview create(PerformanceReview review) {
        if (review == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Performance review is required");
        }

        if (review.getScore() == null
                || review.getScore() < 1
                || review.getScore() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Score must be between 1 and 100");
        }

        if (review.getFeedback() == null
                || review.getFeedback().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Feedback is required");
        }

        if (review.getReviewDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Review date is required");
        }

        if (review.getEmployee() == null
                || review.getEmployee().getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee ID is required");
        }

        if (review.getReviewYear() == null
                || review.getReviewYear() < 2000
                || review.getReviewYear() > 2100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Review year is invalid");
        }

        if (review.getEmployee() != null
                && review.getEmployee().getId() != null) {
            Employee employee = employeeRepository
                    .findById(review.getEmployee().getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Employee not found"));

            review.setEmployee(employee);
        }

        return reviewRepository.save(review);
    }

    public List<PerformanceReview> findAll() {
        return reviewRepository.findAll();
    }

    public List<PerformanceReview> findByEmployee(Long employeeId) {
        validateEmployee(employeeId);
        return reviewRepository.findByEmployee_IdOrderByReviewDateAscIdAsc(
                employeeId);
    }

    /**
     * Builds a performance scorecard for an employee.
     *
     * Includes:
     * - number of reviews
     * - average score
     * - highest score
     * - lowest score
     * - latest score
     * - score trend
     */
    public Map<String, Object> getScorecard(Long employeeId) {
        List<PerformanceReview> reviews = findByEmployee(employeeId);

        Map<String, Object> scorecard = new LinkedHashMap<>();

        scorecard.put("employeeId", employeeId);
        scorecard.put("reviewCount", reviews.size());

        if (reviews.isEmpty()) {
            scorecard.put("averageScore", 0.0);
            scorecard.put("highestScore", 0);
            scorecard.put("lowestScore", 0);
            scorecard.put("latestScore", 0);
            scorecard.put("trend", "NO_DATA");
            scorecard.put("reviews", reviews);
            return scorecard;
        }

        double average = reviews.stream()
                .mapToInt(PerformanceReview::getScore)
                .average()
                .orElse(0.0);

        int highest = reviews.stream()
                .mapToInt(PerformanceReview::getScore)
                .max()
                .orElse(0);

        int lowest = reviews.stream()
                .mapToInt(PerformanceReview::getScore)
                .min()
                .orElse(0);

        int latest = reviews.get(reviews.size() - 1).getScore();

        String trend = calculateTrend(reviews);

        scorecard.put("averageScore", Math.round(average * 100.0) / 100.0);
        scorecard.put("highestScore", highest);
        scorecard.put("lowestScore", lowest);
        scorecard.put("latestScore", latest);
        scorecard.put("trend", trend);
        scorecard.put("reviews", reviews);

        return scorecard;
    }

    private String calculateTrend(List<PerformanceReview> reviews) {
        if (reviews.size() < 2) {
            return "STABLE";
        }

        int previous = reviews.get(reviews.size() - 2).getScore();
        int latest = reviews.get(reviews.size() - 1).getScore();

        if (latest > previous) {
            return "IMPROVING";
        }

        if (latest < previous) {
            return "DECLINING";
        }

        return "STABLE";
    }

    private void validateEmployee(Long employeeId) {
        if (employeeId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee ID is required");
        }

        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found"));
    }
}
