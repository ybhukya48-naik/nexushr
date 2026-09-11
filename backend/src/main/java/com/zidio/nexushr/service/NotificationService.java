package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationDeliveryStatus;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final EmployeeRepository employeeRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationDeliveryService notificationDeliveryService,
            EmployeeRepository employeeRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.employeeRepository = employeeRepository;
    }

    public Notification createNotification(
            Long employeeId,
            String title,
            String message) {

        Notification notification = new Notification();
        notification.setEmployeeId(employeeId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationType(NotificationType.GENERAL);
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
        notification.setSentAt(LocalDateTime.now());

        return notificationRepository.save(notification);
    }

    /*
     * Backward-compatible overload used by existing leave, payroll
     * and reminder notification workflows.
     */
    public Notification sendNotification(
            Long employeeId,
            String title,
            String message,
            NotificationType notificationType,
            NotificationChannel channel,
            String recipientEmail,
            String recipientPhone) {

        return sendNotification(
                employeeId,
                title,
                message,
                notificationType,
                channel,
                recipientEmail,
                recipientPhone,
                null);
    }
    public Notification sendNotification(
            Long employeeId,
            String title,
            String message,
            NotificationType notificationType,
            NotificationChannel channel,
            String recipientEmail,
            String recipientPhone,
            LocalDateTime scheduledAt) {

        Notification notification = new Notification();

        notification.setEmployeeId(employeeId);
        notification.setTitle(title);
        notification.setMessage(message);

        notification.setNotificationType(
                notificationType != null
                        ? notificationType
                        : NotificationType.GENERAL);

        notification.setChannel(
                channel != null
                        ? channel
                        : NotificationChannel.IN_APP);

        notification.setRecipientEmail(recipientEmail);
        notification.setRecipientPhone(recipientPhone);
        notification.setScheduledAt(scheduledAt);

        /*
         * A future scheduled notification is persisted as PENDING.
         * The scheduler will deliver it when its scheduled time arrives.
         */
        if (scheduledAt != null &&
                scheduledAt.isAfter(LocalDateTime.now())) {

            notification.setDeliveryStatus(
                    NotificationDeliveryStatus.PENDING);

            return notificationRepository.save(notification);
        }

        /*
         * No future schedule means deliver immediately.
         */
        if (notification.getChannel() == NotificationChannel.IN_APP) {
            notification.setDeliveryStatus(
                    NotificationDeliveryStatus.SENT);
            notification.setSentAt(LocalDateTime.now());

            return notificationRepository.save(notification);
        }

        notification.setDeliveryStatus(
                NotificationDeliveryStatus.PENDING);

        notification = notificationRepository.save(notification);

        notification = notificationDeliveryService.deliver(notification);

        return notificationRepository.save(notification);
    }

    public List<Notification> sendBroadcastAnnouncement(
            String title,
            String message,
            NotificationChannel channel) {

        List<Notification> notifications = new ArrayList<>();

        for (Employee employee : employeeRepository.findAll()) {

            if (!employee.isActive()) {
                continue;
            }

            Notification notification = sendNotification(
                    employee.getId(),
                    title,
                    message,
                    NotificationType.ANNOUNCEMENT,
                    channel,
                    employee.getEmail(),
                    employee.getPhone(),
                    null);

            notifications.add(notification);
        }

        return notifications;
    }

    public List<Notification> getNotificationsForEmployee(Long employeeId) {
        return notificationRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    public List<Notification> getUnreadNotificationsForEmployee(Long employeeId) {
        return notificationRepository
                .findByEmployeeIdAndReadFalseOrderByCreatedAtDesc(employeeId);
    }

    public Notification markAsRead(Long id) {
        Notification notification =
                notificationRepository.findById(id).orElse(null);

        if (notification != null) {
            notification.setRead(true);
            return notificationRepository.save(notification);
        }

        return null;
    }

    public void processScheduledNotifications() {

        LocalDateTime now = LocalDateTime.now();

        List<Notification> dueNotifications =
                notificationRepository
                        .findByScheduledAtLessThanEqualAndDeliveryStatusOrderByScheduledAtAsc(
                                now,
                                NotificationDeliveryStatus.PENDING);

        for (Notification notification : dueNotifications) {

            notification = notificationDeliveryService.deliver(notification);

            notificationRepository.save(notification);
        }
    }
}
