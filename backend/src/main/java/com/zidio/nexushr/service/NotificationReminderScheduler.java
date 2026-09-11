package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PerformanceReviewRepository;
import com.zidio.nexushr.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class NotificationReminderScheduler {

    private final EmployeeRepository employeeRepository;
    private final PerformanceReviewRepository performanceReviewRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public NotificationReminderScheduler(
            EmployeeRepository employeeRepository,
            PerformanceReviewRepository performanceReviewRepository,
            NotificationRepository notificationRepository,
            NotificationService notificationService) {

        this.employeeRepository = employeeRepository;
        this.performanceReviewRepository = performanceReviewRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    /**
     * Runs every day at 09:00.
     *
     * Sends a reminder one day before a scheduled performance review.
     */
    @Scheduled(cron = "${app.notifications.performance-review-reminder-cron:0 0 9 * * *}")
    public void sendPerformanceReviewReminders() {

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        for (Employee employee : employeeRepository.findAll()) {

            if (!employee.isActive() || employee.getId() == null) {
                continue;
            }

            List<PerformanceReview> reviews =
                    performanceReviewRepository
                            .findByEmployee_Id(employee.getId());

            for (PerformanceReview review : reviews) {

                if (review.getReviewDate() == null
                        || !tomorrow.equals(review.getReviewDate())) {
                    continue;
                }

                String title = "Performance Review Reminder";

                boolean alreadySent =
                        notificationRepository
                                .existsByEmployeeIdAndNotificationTypeAndMessageContaining(
                                        employee.getId(),
                                        NotificationType.REMINDER,
                                        review.getReviewDate().toString());

                if (alreadySent) {
                    continue;
                }

                notificationService.sendNotification(
                        employee.getId(),
                        title,
                        "Your performance review is scheduled for "
                                + review.getReviewDate()
                                + ". Please complete any required preparation.",
                        NotificationType.REMINDER,
                        NotificationChannel.BOTH,
                        employee.getEmail(),
                        employee.getPhone());
            }
        }
    }
}
