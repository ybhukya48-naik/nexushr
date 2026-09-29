package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationDeliveryStatus;
import com.zidio.nexushr.service.email.ResendEmailService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationDeliveryService {

    private final ResendEmailService emailService;

    public NotificationDeliveryService(
            ResendEmailService emailService) {
        this.emailService = emailService;
    }

    public Notification deliver(Notification notification) {

        notification.setDeliveryStatus(
                NotificationDeliveryStatus.PENDING
        );

        try {

            NotificationChannel channel =
                    notification.getChannel();

            System.out.println(
                    "NOTIFICATION_DELIVERY_START: channel="
                            + channel
                            + " employeeId="
                            + notification.getEmployeeId()
            );

            if (channel == NotificationChannel.EMAIL ||
                channel == NotificationChannel.BOTH) {

                sendEmail(notification);
            }

            if (channel == NotificationChannel.SMS ||
                channel == NotificationChannel.BOTH) {

                sendSms(notification);
            }

            notification.setDeliveryStatus(
                    NotificationDeliveryStatus.SENT
            );

            notification.setSentAt(
                    LocalDateTime.now()
            );

            notification.setFailureReason(null);

            System.out.println(
                    "NOTIFICATION_DELIVERY_SUCCESS: channel="
                            + channel
                            + " employeeId="
                            + notification.getEmployeeId()
            );

        } catch (Exception ex) {

            notification.setDeliveryStatus(
                    NotificationDeliveryStatus.FAILED
            );

            notification.setFailureReason(
                    ex.getMessage()
            );

            System.err.println(
                    "NOTIFICATION_DELIVERY_FAILED: "
                            + ex.getClass().getSimpleName()
                            + ": "
                            + ex.getMessage()
            );

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

        System.out.println(
                "NOTIFICATION_EMAIL_SEND: recipient="
                        + maskEmail(notification.getRecipientEmail())
        );

        String resendId = emailService.sendTextEmail(
                notification.getRecipientEmail(),
                notification.getTitle(),
                notification.getMessage()
        );

        System.out.println(
                "NOTIFICATION_EMAIL_RESEND_SUCCESS: resendId="
                        + resendId
        );
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
         * For now, simulate success.
         */
        System.out.println(
                "NOTIFICATION_SMS_SIMULATED: employeeId="
                        + notification.getEmployeeId()
        );
    }

    private String maskEmail(String email) {

        String trimmed = email.trim();

        int at = trimmed.indexOf('@');

        if (at <= 1) {
            return "***";
        }

        return trimmed.charAt(0)
                + "***"
                + trimmed.substring(at - 1);
    }
}
