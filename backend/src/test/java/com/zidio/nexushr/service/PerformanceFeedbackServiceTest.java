package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PerformanceFeedback;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PerformanceFeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceFeedbackServiceTest {

    @Mock
    private PerformanceFeedbackRepository feedbackRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private PerformanceFeedbackService performanceFeedbackService;

    private Employee employee;
    private Employee reviewer;
    private PerformanceFeedback feedback;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);

        reviewer = new Employee();
        reviewer.setId(2L);

        feedback = new PerformanceFeedback();
        feedback.setEmployee(employee);
        feedback.setReviewer(reviewer);
        feedback.setRating(5);
        feedback.setComments("Excellent teamwork and communication.");
    }

    @Test
    void create_savesFeedbackAndSetsSubmissionTime() {
        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(employeeRepository.findById(2L))
                .thenReturn(Optional.of(reviewer));

        when(feedbackRepository.save(feedback))
                .thenReturn(feedback);

        PerformanceFeedback result =
                performanceFeedbackService.create(feedback);

        assertThat(result).isSameAs(feedback);
        assertThat(result.getSubmittedAt()).isNotNull();

        verify(employeeRepository).findById(1L);
        verify(employeeRepository).findById(2L);
        verify(feedbackRepository).save(feedback);
    }

    @Test
    void create_rejectsSelfFeedback() {
        feedback.setReviewer(employee);

        assertThatThrownBy(() ->
                performanceFeedbackService.create(feedback))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(
                        "Reviewer and employee must be different");

        verifyNoInteractions(employeeRepository, feedbackRepository);
    }

    @Test
    void create_rejectsRatingBelowOne() {
        feedback.setRating(0);

        assertThatThrownBy(() ->
                performanceFeedbackService.create(feedback))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(
                        "Rating must be between 1 and 5");

        verifyNoInteractions(employeeRepository, feedbackRepository);
    }

    @Test
    void create_rejectsRatingAboveFive() {
        feedback.setRating(6);

        assertThatThrownBy(() ->
                performanceFeedbackService.create(feedback))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(
                        "Rating must be between 1 and 5");

        verifyNoInteractions(employeeRepository, feedbackRepository);
    }

    @Test
    void create_rejectsBlankComments() {
        feedback.setComments("   ");

        assertThatThrownBy(() ->
                performanceFeedbackService.create(feedback))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Comments are required");

        verifyNoInteractions(employeeRepository, feedbackRepository);
    }

    @Test
    void create_rejectsMissingEmployee() {
        feedback.setEmployee(null);

        assertThatThrownBy(() ->
                performanceFeedbackService.create(feedback))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Employee ID is required");

        verifyNoInteractions(employeeRepository, feedbackRepository);
    }

    @Test
    void create_rejectsMissingReviewer() {
        feedback.setReviewer(null);

        assertThatThrownBy(() ->
                performanceFeedbackService.create(feedback))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Reviewer ID is required");

        verifyNoInteractions(employeeRepository, feedbackRepository);
    }

    @Test
    void create_rejectsUnknownEmployee() {
        when(employeeRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                performanceFeedbackService.create(feedback))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Employee not found");

        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void create_rejectsUnknownReviewer() {
        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(employeeRepository.findById(2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                performanceFeedbackService.create(feedback))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Reviewer not found");

        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void findAll_returnsFeedbackList() {
        when(feedbackRepository.findAll())
                .thenReturn(List.of(feedback));

        List<PerformanceFeedback> result =
                performanceFeedbackService.findAll();

        assertThat(result).containsExactly(feedback);
        verify(feedbackRepository).findAll();
    }

    @Test
    void findByEmployee_returnsFeedbackList() {
        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(feedbackRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(feedback));

        List<PerformanceFeedback> result =
                performanceFeedbackService.findByEmployee(1L);

        assertThat(result).containsExactly(feedback);
        verify(feedbackRepository).findByEmployee_Id(1L);
    }

    @Test
    void findByReviewer_returnsFeedbackList() {
        when(employeeRepository.findById(2L))
                .thenReturn(Optional.of(reviewer));

        when(feedbackRepository.findByReviewer_Id(2L))
                .thenReturn(List.of(feedback));

        List<PerformanceFeedback> result =
                performanceFeedbackService.findByReviewer(2L);

        assertThat(result).containsExactly(feedback);
        verify(feedbackRepository).findByReviewer_Id(2L);
    }
}
