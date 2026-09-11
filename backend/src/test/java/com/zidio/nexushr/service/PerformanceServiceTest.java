package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PerformanceReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    @Mock
    private PerformanceReviewRepository reviewRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private PerformanceService performanceService;

    private PerformanceReview review;

    @BeforeEach
    void setUp() {
        Employee employee = new Employee();
        employee.setId(1L);

        review = new PerformanceReview();
        review.setId(1L);
        review.setEmployee(employee);
        review.setReviewYear(2026);
        review.setScore(85);
        review.setFeedback("Excellent performance");
        review.setReviewDate(LocalDate.of(2026, 6, 30));
    }

    @Test
    void create_savesAndReturnsReview() {
        Employee employee = review.getEmployee();

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));
        when(reviewRepository.save(review))
                .thenReturn(review);

        PerformanceReview result = performanceService.create(review);

        assertThat(result).isSameAs(review);
        verify(employeeRepository).findById(1L);
        verify(reviewRepository).save(review);
    }

    @Test
    void findAll_returnsList() {
        when(reviewRepository.findAll()).thenReturn(List.of(review));

        List<PerformanceReview> result = performanceService.findAll();

        assertThat(result).containsExactly(review);
        verify(reviewRepository).findAll();
    }

    @Test
    void findAll_returnsEmptyList_whenNoReviews() {
        when(reviewRepository.findAll()).thenReturn(List.of());

        assertThat(performanceService.findAll()).isEmpty();
    }
}
