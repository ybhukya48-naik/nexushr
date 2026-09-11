package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationDeliveryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private NotificationDeliveryService service() {
        return new NotificationDeliveryService(mailSender);
    }

    @Test
    void emailNotificationShouldBeSent() {

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

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        SimpleMailMessage mail = captor.getValue();

        assertArrayEquals(
                new String[]{"employee@example.com"},
                mail.getTo());

        assertEquals("Approval", mail.getSubject());
        assertEquals(
                "Your leave was approved.",
                mail.getText());
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

        verifyNoInteractions(mailSender);
    }

    @Test
    void bothNotificationShouldSendEmailAndSms() {

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

        verify(mailSender).send(any(SimpleMailMessage.class));
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

        verifyNoInteractions(mailSender);
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

        verifyNoInteractions(mailSender);
    }

    @Test
    void emailSenderFailureShouldMarkNotificationFailed() {

        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setRecipientEmail("employee@example.com");
        notification.setTitle("Test");
        notification.setMessage("Test message.");

        doThrow(new RuntimeException("SMTP connection failed"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        Notification result = service().deliver(notification);

        assertEquals(
                NotificationDeliveryStatus.FAILED,
                result.getDeliveryStatus());

        assertEquals(
                "SMTP connection failed",
                result.getFailureReason());

        assertNull(result.getSentAt());

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
