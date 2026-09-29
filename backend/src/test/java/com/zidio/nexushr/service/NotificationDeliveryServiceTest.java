package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationDeliveryStatus;
import com.zidio.nexushr.service.email.ResendEmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock
    private ResendEmailService emailService;

    private NotificationDeliveryService service() {
        return new NotificationDeliveryService(emailService);
    }

    @Test
    void emailNotificationShouldBeSent() {

        when(emailService.sendTextEmail(
                anyString(),
                anyString(),
                anyString()
        )).thenReturn("resend-email-id");

        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setRecipientEmail("employee@example.com");
        notification.setTitle("Approval");
        notification.setMessage("Your leave was approved.");

        Notification result = service().deliver(notification);

        assertEquals(
                NotificationDeliveryStatus.SENT,
                result.getDeliveryStatus());

        assertNotNull(result.getSentAt());
        assertNull(result.getFailureReason());

        ArgumentCaptor<String> toCaptor =
                ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<String> subjectCaptor =
                ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<String> messageCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(emailService).sendTextEmail(
                toCaptor.capture(),
                subjectCaptor.capture(),
                messageCaptor.capture()
        );

        assertEquals(
                "employee@example.com",
                toCaptor.getValue());

        assertEquals(
                "Approval",
                subjectCaptor.getValue());

        assertEquals(
                "Your leave was approved.",
                messageCaptor.getValue());
    }

    @Test
    void smsNotificationShouldBeSent() {

        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.SMS);
        notification.setRecipientPhone("9999999999");
        notification.setTitle("Reminder");
        notification.setMessage("Please submit your timesheet.");

        Notification result = service().deliver(notification);

        assertEquals(
                NotificationDeliveryStatus.SENT,
                result.getDeliveryStatus());

        assertNotNull(result.getSentAt());
        assertNull(result.getFailureReason());

        verifyNoInteractions(emailService);
    }

    @Test
    void bothNotificationShouldSendEmailAndSms() {

        when(emailService.sendTextEmail(
                anyString(),
                anyString(),
                anyString()
        )).thenReturn("resend-email-id");

        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.BOTH);
        notification.setRecipientEmail("employee@example.com");
        notification.setRecipientPhone("9999999999");
        notification.setTitle("Announcement");
        notification.setMessage("Company meeting tomorrow.");

        Notification result = service().deliver(notification);

        assertEquals(
                NotificationDeliveryStatus.SENT,
                result.getDeliveryStatus());

        assertNotNull(result.getSentAt());
        assertNull(result.getFailureReason());

        verify(emailService).sendTextEmail(
                "employee@example.com",
                "Announcement",
                "Company meeting tomorrow."
        );
    }

    @Test
    void missingEmailShouldFailEmailNotification() {

        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setTitle("Approval");
        notification.setMessage("Approved.");

        Notification result = service().deliver(notification);

        assertEquals(
                NotificationDeliveryStatus.FAILED,
                result.getDeliveryStatus());

        assertNotNull(result.getFailureReason());

        assertTrue(
                result.getFailureReason()
                        .contains("Recipient email is required"));

        verifyNoInteractions(emailService);
    }

    @Test
    void missingPhoneShouldFailSmsNotification() {

        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.SMS);
        notification.setTitle("Reminder");
        notification.setMessage("Reminder message.");

        Notification result = service().deliver(notification);

        assertEquals(
                NotificationDeliveryStatus.FAILED,
                result.getDeliveryStatus());

        assertNotNull(result.getFailureReason());

        assertTrue(
                result.getFailureReason()
                        .contains("Recipient phone is required"));

        verifyNoInteractions(emailService);
    }

    @Test
    void emailSenderFailureShouldMarkNotificationFailed() {

        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setRecipientEmail("employee@example.com");
        notification.setTitle("Test");
        notification.setMessage("Test message.");

        when(emailService.sendTextEmail(
                "employee@example.com",
                "Test",
                "Test message."
        )).thenThrow(
                new RuntimeException("Resend email sending failed")
        );

        Notification result = service().deliver(notification);

        assertEquals(
                NotificationDeliveryStatus.FAILED,
                result.getDeliveryStatus());

        assertEquals(
                "Resend email sending failed",
                result.getFailureReason());

        assertNull(result.getSentAt());

        verify(emailService).sendTextEmail(
                "employee@example.com",
                "Test",
                "Test message."
        );
    }
}
