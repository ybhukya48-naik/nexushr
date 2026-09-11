package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.NotificationRepository;
import com.zidio.nexushr.repository.PerformanceReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationReminderSchedulerTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PerformanceReviewRepository performanceReviewRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationReminderScheduler scheduler;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);
        employee.setEmail("test@example.com");
        employee.setPhone("9999999999");
        employee.setActive(true);
    }

    @Test
    void shouldSendPerformanceReviewReminderOneDayBeforeReview() {
        PerformanceReview review = new PerformanceReview();
        review.setId(10L);
        review.setEmployee(employee);
        review.setReviewYear(LocalDate.now().plusDays(1).getYear());
        review.setScore(80);
        review.setFeedback("Good performance");
        review.setReviewDate(LocalDate.now().plusDays(1));

        when(employeeRepository.findAll()).thenReturn(List.of(employee));
        when(performanceReviewRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(review));
        when(notificationRepository
                .existsByEmployeeIdAndNotificationTypeAndMessageContaining(
                        eq(1L),
                        eq(NotificationType.REMINDER),
                        eq(review.getReviewDate().toString())))
                .thenReturn(false);

        Notification notification = new Notification();
        notification.setEmployeeId(1L);

        when(notificationService.sendNotification(
                eq(1L),
                eq("Performance Review Reminder"),
                anyString(),
                eq(NotificationType.REMINDER),
                eq(NotificationChannel.BOTH),
                eq("test@example.com"),
                eq("9999999999")))
                .thenReturn(notification);

        scheduler.sendPerformanceReviewReminders();

        verify(notificationService).sendNotification(
                eq(1L),
                eq("Performance Review Reminder"),
                contains(review.getReviewDate().toString()),
                eq(NotificationType.REMINDER),
                eq(NotificationChannel.BOTH),
                eq("test@example.com"),
                eq("9999999999"));
    }

    @Test
    void shouldNotSendReminderWhenReviewIsNotTomorrow() {
        PerformanceReview review = new PerformanceReview();
        review.setId(11L);
        review.setEmployee(employee);
        review.setReviewYear(LocalDate.now().getYear());
        review.setScore(80);
        review.setFeedback("Good performance");
        review.setReviewDate(LocalDate.now());

        when(employeeRepository.findAll()).thenReturn(List.of(employee));
        when(performanceReviewRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(review));

        scheduler.sendPerformanceReviewReminders();

        verify(notificationService, never()).sendNotification(
                anyLong(),
                anyString(),
                anyString(),
                any(NotificationType.class),
                any(NotificationChannel.class),
                anyString(),
                anyString());
    }

    @Test
    void shouldNotSendDuplicateReminder() {
        PerformanceReview review = new PerformanceReview();
        review.setId(12L);
        review.setEmployee(employee);
        review.setReviewYear(LocalDate.now().plusDays(1).getYear());
        review.setScore(85);
        review.setFeedback("Strong performance");
        review.setReviewDate(LocalDate.now().plusDays(1));

        when(employeeRepository.findAll()).thenReturn(List.of(employee));
        when(performanceReviewRepository.findByEmployee_Id(1L))
                .thenReturn(List.of(review));
        when(notificationRepository
                .existsByEmployeeIdAndNotificationTypeAndMessageContaining(
                        eq(1L),
                        eq(NotificationType.REMINDER),
                        eq(review.getReviewDate().toString())))
                .thenReturn(true);

        scheduler.sendPerformanceReviewReminders();

        verify(notificationService, never()).sendNotification(
                anyLong(),
                anyString(),
                anyString(),
                any(NotificationType.class),
                any(NotificationChannel.class),
                anyString(),
                anyString());
    }

    @Test
    void shouldSkipInactiveEmployees() {
        employee.setActive(false);

        when(employeeRepository.findAll()).thenReturn(List.of(employee));

        scheduler.sendPerformanceReviewReminders();

        verifyNoInteractions(performanceReviewRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldSupportMultipleActiveEmployees() {
        Employee secondEmployee = new Employee();
        secondEmployee.setId(2L);
        secondEmployee.setEmail("second@example.com");
        secondEmployee.setPhone("8888888888");
        secondEmployee.setActive(true);

        when(employeeRepository.findAll())
                .thenReturn(List.of(employee, secondEmployee));

        when(performanceReviewRepository.findByEmployee_Id(1L))
                .thenReturn(List.of());

        when(performanceReviewRepository.findByEmployee_Id(2L))
                .thenReturn(List.of());

        scheduler.sendPerformanceReviewReminders();

        verify(performanceReviewRepository).findByEmployee_Id(1L);
        verify(performanceReviewRepository).findByEmployee_Id(2L);
        verifyNoInteractions(notificationService);
    }
}
