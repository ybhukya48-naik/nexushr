package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PerformanceGoal;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PerformanceGoalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class PerformanceGoalService {

    private final PerformanceGoalRepository goalRepository;
    private final EmployeeRepository employeeRepository;

    public PerformanceGoalService(
            PerformanceGoalRepository goalRepository,
            EmployeeRepository employeeRepository) {

        this.goalRepository = goalRepository;
        this.employeeRepository = employeeRepository;
    }

    public PerformanceGoal create(PerformanceGoal goal) {

        if (goal == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Performance goal is required"
            );
        }

        if (goal.getEmployee() == null
                || goal.getEmployee().getId() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee ID is required"
            );
        }

        if (goal.getTitle() == null
                || goal.getTitle().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Goal title is required"
            );
        }

        if (goal.getDescription() == null
                || goal.getDescription().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Goal description is required"
            );
        }

        if (goal.getTargetScore() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Target score is required"
            );
        }

        if (goal.getTargetScore() < 1
                || goal.getTargetScore() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Target score must be between 1 and 100"
            );
        }

        if (goal.getAchievedScore() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Achieved score is required"
            );
        }

        if (goal.getAchievedScore() < 0
                || goal.getAchievedScore() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Achieved score must be between 0 and 100"
            );
        }

        if (goal.getDueDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Due date is required"
            );
        }

        Employee employee = employeeRepository
                .findById(goal.getEmployee().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found: "
                                + goal.getEmployee().getId()
                ));

        goal.setEmployee(employee);

        return goalRepository.save(goal);
    }

    public List<PerformanceGoal> findAll() {
        return goalRepository.findAll();
    }

    public List<PerformanceGoal> findByEmployee(Long employeeId) {

        validateEmployee(employeeId);

        return goalRepository.findByEmployee_Id(employeeId);
    }

    public PerformanceGoal updateProgress(
            Long goalId,
            Integer achievedScore,
            Boolean completed) {

        if (goalId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Goal ID is required"
            );
        }

        if (achievedScore == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Achieved score is required"
            );
        }

        if (achievedScore < 0 || achievedScore > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Achieved score must be between 0 and 100"
            );
        }

        if (completed == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Completed status is required"
            );
        }

        PerformanceGoal goal =
                goalRepository.findById(goalId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Performance goal not found: " + goalId
                        ));

        goal.setAchievedScore(achievedScore);
        goal.setCompleted(completed);

        return goalRepository.save(goal);
    }

    private void validateEmployee(Long employeeId) {

        if (employeeId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Employee ID is required"
            );
        }

        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found: " + employeeId
                ));
    }
}
