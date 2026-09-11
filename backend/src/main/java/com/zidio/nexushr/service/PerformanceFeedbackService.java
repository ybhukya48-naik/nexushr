package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PerformanceFeedback;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PerformanceFeedbackRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PerformanceFeedbackService {

    private final PerformanceFeedbackRepository feedbackRepository;
    private final EmployeeRepository employeeRepository;

    public PerformanceFeedbackService(
            PerformanceFeedbackRepository feedbackRepository,
            EmployeeRepository employeeRepository) {

        this.feedbackRepository = feedbackRepository;
        this.employeeRepository = employeeRepository;
    }

    public PerformanceFeedback create(PerformanceFeedback feedback) {

        if (feedback == null) {
            throw badRequest("Performance feedback is required");
        }

        if (feedback.getEmployee() == null
                || feedback.getEmployee().getId() == null) {
            throw badRequest("Employee ID is required");
        }

        if (feedback.getReviewer() == null
                || feedback.getReviewer().getId() == null) {
            throw badRequest("Reviewer ID is required");
        }

        if (feedback.getEmployee().getId()
                .equals(feedback.getReviewer().getId())) {
            throw badRequest("Reviewer and employee must be different");
        }

        if (feedback.getRating() == null) {
            throw badRequest("Rating is required");
        }

        if (feedback.getRating() < 1 || feedback.getRating() > 5) {
            throw badRequest("Rating must be between 1 and 5");
        }

        if (feedback.getComments() == null
                || feedback.getComments().isBlank()) {
            throw badRequest("Comments are required");
        }

        Employee employee = employeeRepository
                .findById(feedback.getEmployee().getId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Employee not found"
                        ));

        Employee reviewer = employeeRepository
                .findById(feedback.getReviewer().getId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Reviewer not found"
                        ));

        feedback.setEmployee(employee);
        feedback.setReviewer(reviewer);

        // The server controls the submission timestamp.
        feedback.setSubmittedAt(LocalDateTime.now());

        return feedbackRepository.save(feedback);
    }

    public List<PerformanceFeedback> findAll() {
        return feedbackRepository.findAll();
    }

    public List<PerformanceFeedback> findByEmployee(Long employeeId) {

        validateEmployee(employeeId);

        return feedbackRepository.findByEmployee_Id(employeeId);
    }

    public List<PerformanceFeedback> findByReviewer(Long reviewerId) {

        validateEmployee(reviewerId);

        return feedbackRepository.findByReviewer_Id(reviewerId);
    }

    private void validateEmployee(Long employeeId) {

        if (employeeId == null) {
            throw badRequest("Employee ID is required");
        }

        employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Employee not found"
                        ));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
