package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationDeliveryStatus;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createNotificationShouldCreateInAppNotification() {

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.createNotification(
                1L,
                "Test",
                "Test message");

        assertNotNull(result);
        assertEquals(1L, result.getEmployeeId());
        assertEquals("Test", result.getTitle());
        assertEquals("Test message", result.getMessage());
        assertEquals(NotificationChannel.IN_APP, result.getChannel());
        assertEquals(NotificationType.GENERAL, result.getNotificationType());
        assertEquals(
                NotificationDeliveryStatus.SENT,
                result.getDeliveryStatus());

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void sendInAppNotificationShouldNotInvokeExternalDelivery() {

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.sendNotification(
                1L,
                "In App",
                "Message",
                NotificationType.GENERAL,
                NotificationChannel.IN_APP,
                null,
                null);

        assertEquals(NotificationChannel.IN_APP, result.getChannel());
        assertEquals(
                NotificationDeliveryStatus.SENT,
                result.getDeliveryStatus());

        verify(notificationDeliveryService, never())
                .deliver(any(Notification.class));
    }

    @Test
    void sendEmailNotificationShouldInvokeDelivery() {

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(notificationDeliveryService.deliver(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.sendNotification(
                1L,
                "Email",
                "Email message",
                NotificationType.APPROVAL,
                NotificationChannel.EMAIL,
                "employee@example.com",
                null);

        assertEquals(NotificationChannel.EMAIL, result.getChannel());
        assertEquals(
                NotificationType.APPROVAL,
                result.getNotificationType());
        assertEquals(
                "employee@example.com",
                result.getRecipientEmail());

        verify(notificationDeliveryService)
                .deliver(any(Notification.class));

        verify(notificationRepository, times(2))
                .save(any(Notification.class));
    }

    @Test
    void sendSmsNotificationShouldInvokeDelivery() {

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(notificationDeliveryService.deliver(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.sendNotification(
                1L,
                "SMS",
                "SMS message",
                NotificationType.REMINDER,
                NotificationChannel.SMS,
                null,
                "9999999999");

        assertEquals(NotificationChannel.SMS, result.getChannel());
        assertEquals(
                NotificationType.REMINDER,
                result.getNotificationType());
        assertEquals(
                "9999999999",
                result.getRecipientPhone());

        verify(notificationDeliveryService)
                .deliver(any(Notification.class));

        verify(notificationRepository, times(2))
                .save(any(Notification.class));
    }

    @Test
    void sendBothNotificationShouldInvokeDelivery() {

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(notificationDeliveryService.deliver(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.sendNotification(
                1L,
                "Both",
                "Both message",
                NotificationType.ANNOUNCEMENT,
                NotificationChannel.BOTH,
                "employee@example.com",
                "9999999999");

        assertEquals(NotificationChannel.BOTH, result.getChannel());
        assertEquals(
                NotificationType.ANNOUNCEMENT,
                result.getNotificationType());
        assertEquals(
                "employee@example.com",
                result.getRecipientEmail());
        assertEquals(
                "9999999999",
                result.getRecipientPhone());

        verify(notificationDeliveryService)
                .deliver(any(Notification.class));

        verify(notificationRepository, times(2))
                .save(any(Notification.class));
    }

    @Test
    void broadcastShouldSendAnnouncementToActiveEmployeesOnly() {

        Employee active = new Employee();
        active.setId(1L);
        active.setActive(true);
        active.setEmail("active@example.com");
        active.setPhone("9999999999");

        Employee inactive = new Employee();
        inactive.setId(2L);
        inactive.setActive(false);
        inactive.setEmail("inactive@example.com");
        inactive.setPhone("8888888888");

        when(employeeRepository.findAll())
                .thenReturn(List.of(active, inactive));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(notificationDeliveryService.deliver(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Notification> result =
                notificationService.sendBroadcastAnnouncement(
                        "Company Announcement",
                        "Important announcement",
                        NotificationChannel.EMAIL);

        assertEquals(1, result.size());
        assertEquals(
                NotificationChannel.EMAIL,
                result.get(0).getChannel());
        assertEquals(
                NotificationType.ANNOUNCEMENT,
                result.get(0).getNotificationType());
        assertEquals(
                "active@example.com",
                result.get(0).getRecipientEmail());

        verify(employeeRepository).findAll();

        verify(notificationDeliveryService, times(1))
                .deliver(any(Notification.class));
    }
}
