package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationDeliveryStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationDeliveryService {

    private final JavaMailSender mailSender;

    public NotificationDeliveryService(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public Notification deliver(Notification notification) {
        notification.setDeliveryStatus(NotificationDeliveryStatus.PENDING);

        try {
            NotificationChannel channel = notification.getChannel();

            if (channel == NotificationChannel.EMAIL ||
                channel == NotificationChannel.BOTH) {
                sendEmail(notification);
            }

            if (channel == NotificationChannel.SMS ||
                channel == NotificationChannel.BOTH) {
                sendSms(notification);
            }

            notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notification.setFailureReason(null);

        } catch (Exception ex) {
            notification.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
            notification.setFailureReason(ex.getMessage());
        }

        return notification;
    }

    private void sendEmail(Notification notification) {
        if (notification.getRecipientEmail() == null ||
            notification.getRecipientEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "Recipient email is required for EMAIL/BOTH notification"
            );
        }

        if (mailSender == null) {
            throw new IllegalStateException(
                    "Email service is not configured"
            );
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(notification.getRecipientEmail());
        mail.setSubject(notification.getTitle());
        mail.setText(notification.getMessage());

        mailSender.send(mail);
    }

    private void sendSms(Notification notification) {
        if (notification.getRecipientPhone() == null ||
            notification.getRecipientPhone().isBlank()) {
            throw new IllegalArgumentException(
                    "Recipient phone is required for SMS/BOTH notification"
            );
        }

        /*
         * SMS provider integration point.
         *
         * For submission/demo purposes we simulate successful SMS delivery.
         * A real provider such as Twilio/MSG91 can be connected later.
         */
    }
}
