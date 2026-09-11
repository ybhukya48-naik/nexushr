package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.service.NotificationService;
import com.zidio.nexushr.web.dto.NotificationSendRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public ResponseEntity<List<Notification>> getNotifications(
            @PathVariable Long employeeId) {

        return ResponseEntity.ok(
                notificationService.getNotificationsForEmployee(employeeId));
    }

    @GetMapping("/employee/{employeeId}/unread")
    @PreAuthorize("@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public ResponseEntity<List<Notification>> getUnreadNotifications(
            @PathVariable Long employeeId) {

        return ResponseEntity.ok(
                notificationService.getUnreadNotificationsForEmployee(employeeId));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("@authorizationService.canAccessNotification(#id, authentication)")
    public ResponseEntity<Notification> markAsRead(
            @PathVariable Long id) {

        Notification updated = notificationService.markAsRead(id);

        return updated != null
                ? ResponseEntity.ok(updated)
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public ResponseEntity<Notification> sendNotification(
            @RequestBody NotificationSendRequest request) {

        Notification notification;

        if (request.getScheduledAt() != null) {
            notification = notificationService.sendNotification(
                    request.getEmployeeId(),
                    request.getTitle(),
                    request.getMessage(),
                    request.getNotificationType(),
                    request.getChannel(),
                    request.getRecipientEmail(),
                    request.getRecipientPhone(),
                    request.getScheduledAt());
        } else {
            notification = notificationService.sendNotification(
                    request.getEmployeeId(),
                    request.getTitle(),
                    request.getMessage(),
                    request.getNotificationType(),
                    request.getChannel(),
                    request.getRecipientEmail(),
                    request.getRecipientPhone());
        }

        return ResponseEntity.ok(notification);
    }

    @PostMapping("/approval")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public ResponseEntity<Notification> sendApprovalNotification(
            @RequestBody NotificationSendRequest request) {

        Notification notification = notificationService.sendNotification(
                request.getEmployeeId(),
                request.getTitle(),
                request.getMessage(),
                NotificationType.APPROVAL,
                resolveChannel(request),
                request.getRecipientEmail(),
                request.getRecipientPhone(),
                request.getScheduledAt());

        return ResponseEntity.ok(notification);
    }

    @PostMapping("/reminder")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public ResponseEntity<Notification> sendReminderNotification(
            @RequestBody NotificationSendRequest request) {

        Notification notification = notificationService.sendNotification(
                request.getEmployeeId(),
                request.getTitle(),
                request.getMessage(),
                NotificationType.REMINDER,
                resolveChannel(request),
                request.getRecipientEmail(),
                request.getRecipientPhone(),
                request.getScheduledAt());

        return ResponseEntity.ok(notification);
    }

    @PostMapping("/announcement")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public ResponseEntity<Notification> sendAnnouncementNotification(
            @RequestBody NotificationSendRequest request) {

        Notification notification = notificationService.sendNotification(
                request.getEmployeeId(),
                request.getTitle(),
                request.getMessage(),
                NotificationType.ANNOUNCEMENT,
                resolveChannel(request),
                request.getRecipientEmail(),
                request.getRecipientPhone(),
                request.getScheduledAt());

        return ResponseEntity.ok(notification);
    }

    @PostMapping("/announcement/broadcast")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public ResponseEntity<List<Notification>> broadcastAnnouncement(
            @RequestBody NotificationSendRequest request) {

        List<Notification> notifications =
                notificationService.sendBroadcastAnnouncement(
                        request.getTitle(),
                        request.getMessage(),
                        resolveChannel(request));

        return ResponseEntity.ok(notifications);
    }

    private NotificationChannel resolveChannel(NotificationSendRequest request) {
        return request.getChannel() != null
                ? request.getChannel()
                : NotificationChannel.IN_APP;
    }
}
