package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PerformanceReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PerformanceService {

    private static final int STANDARD_DAILY_MINUTES = 480;

    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;

    public PerformanceService(
            PerformanceReviewRepository reviewRepository,
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository) {
        this.reviewRepository = reviewRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
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

        Employee employee = employeeRepository
                .findById(review.getEmployee().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found"));

        review.setEmployee(employee);

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
     * Builds a performance scorecard using real attendance working hours.
     *
     * The main performance score is based on:
     *
     *     actual worked minutes / expected working minutes * 100
     *
     * for the current month-to-date.
     *
     * Manual performance reviews are retained separately as review statistics.
     */
    public Map<String, Object> getScorecard(Long employeeId) {
        Employee employee = validateEmployee(employeeId);

        List<PerformanceReview> reviews = findByEmployee(employeeId);

        Map<String, Object> scorecard = new LinkedHashMap<>();

        scorecard.put("employeeId", employeeId);
        scorecard.put("reviewCount", reviews.size());

        /*
         * ------------------------------------------------------------
         * REAL ATTENDANCE-BASED PERFORMANCE
         * ------------------------------------------------------------
         */
        YearMonth currentMonth = YearMonth.now();

        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate today = LocalDate.now();

        LocalDate monthEnd = currentMonth.atEndOfMonth();

        /*
         * Do not calculate future working days.
         * Performance is month-to-date.
         */
        LocalDate calculationEnd =
                today.isBefore(monthEnd) ? today : monthEnd;

        LocalDate effectiveStart = monthStart;

        if (employee.getJoiningDate() != null
                && employee.getJoiningDate().isAfter(effectiveStart)) {
            effectiveStart = employee.getJoiningDate();
        }

        int expectedWorkMinutes = 0;

        if (!effectiveStart.isAfter(calculationEnd)) {
            int businessDays = businessDaysBetween(
                    effectiveStart,
                    calculationEnd);

            expectedWorkMinutes =
                    businessDays * STANDARD_DAILY_MINUTES;
        }

        List<AttendanceRecord> attendanceRecords =
                attendanceRepository
                        .findByEmployee_IdAndAttendanceDateBetween(
                                employeeId,
                                effectiveStart.isAfter(calculationEnd)
                                        ? calculationEnd
                                        : effectiveStart,
                                calculationEnd);

        int workedMinutes = attendanceRecords.stream()
                .map(AttendanceRecord::getWorkMinutes)
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .sum();

        int overtimeMinutes = attendanceRecords.stream()
                .map(AttendanceRecord::getOvertimeMinutes)
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .sum();

        int shortfallMinutes = Math.max(
                0,
                expectedWorkMinutes - workedMinutes);

        double attendanceScore = 0.0;

        if (expectedWorkMinutes > 0) {
            attendanceScore =
                    (workedMinutes * 100.0) / expectedWorkMinutes;

            /*
             * Overtime does not push the performance score above 100.
             */
            attendanceScore = Math.min(100.0, attendanceScore);
        }

        /*
         * ------------------------------------------------------------
         * MANUAL REVIEW STATISTICS
         * ------------------------------------------------------------
         */
        double reviewAverage = reviews.stream()
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

        int latest = reviews.isEmpty()
                ? 0
                : reviews.get(reviews.size() - 1).getScore();

        String trend = calculateTrend(reviews);

        /*
         * The frontend already uses averageScore for the main
         * Performance Score card.
         *
         * Therefore averageScore now represents the REAL
         * attendance/working-hours performance.
         */
        scorecard.put(
                "averageScore",
                BigDecimal.valueOf(attendanceScore)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue());

        /*
         * Keep manual-review average separately so existing
         * review information is not lost.
         */
        scorecard.put(
                "reviewAverageScore",
                BigDecimal.valueOf(reviewAverage)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue());

        scorecard.put("highestScore", highest);
        scorecard.put("lowestScore", lowest);
        scorecard.put("latestScore", latest);
        scorecard.put("trend", trend);

        /*
         * Attendance intelligence.
         */
        scorecard.put(
                "performanceBasis",
                "REAL_WORKING_HOURS");

        scorecard.put(
                "attendancePeriod",
                currentMonth.toString());

        scorecard.put(
                "attendanceStartDate",
                effectiveStart);

        scorecard.put(
                "attendanceEndDate",
                calculationEnd);

        scorecard.put(
                "expectedWorkMinutes",
                expectedWorkMinutes);

        scorecard.put(
                "workedMinutes",
                workedMinutes);

        scorecard.put(
                "shortfallMinutes",
                shortfallMinutes);

        scorecard.put(
                "overtimeMinutes",
                overtimeMinutes);

        scorecard.put(
                "expectedWorkHours",
                roundHours(expectedWorkMinutes));

        scorecard.put(
                "workedHours",
                roundHours(workedMinutes));

        scorecard.put(
                "shortfallHours",
                roundHours(shortfallMinutes));

        scorecard.put(
                "overtimeHours",
                roundHours(overtimeMinutes));

        scorecard.put("reviews", reviews);

        return scorecard;
    }

    private double roundHours(int minutes) {
        return BigDecimal.valueOf(minutes)
                .divide(
                        BigDecimal.valueOf(60),
                        2,
                        RoundingMode.HALF_UP)
                .doubleValue();
    }

    private int businessDaysBetween(
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            return 0;
        }

        int count = 0;

        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            DayOfWeek day = current.getDayOfWeek();

            if (day != DayOfWeek.SATURDAY
                    && day != DayOfWeek.SUNDAY) {
                count++;
            }

            current = current.plusDays(1);
        }

        return count;
    }

    private String calculateTrend(List<PerformanceReview> reviews) {
        if (reviews.size() < 2) {
            return "STABLE";
        }

        int previous =
                reviews.get(reviews.size() - 2).getScore();

        int latest =
                reviews.get(reviews.size() - 1).getScore();

        if (latest > previous) {
            return "IMPROVING";
        }

        if (latest < previous) {
            return "DECLINING";
        }

        return "STABLE";
    }

    private Employee validateEmployee(Long employeeId) {
        if (employeeId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee ID is required");
        }

        return employeeRepository
                .findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found"));
    }
}
