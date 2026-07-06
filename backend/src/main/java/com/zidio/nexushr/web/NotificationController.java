package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<Notification>> getNotifications(@PathVariable Long employeeId) {
        return ResponseEntity.ok(notificationService.getNotificationsForEmployee(employeeId));
    }

    @GetMapping("/employee/{employeeId}/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications(@PathVariable Long employeeId) {
        return ResponseEntity.ok(notificationService.getUnreadNotificationsForEmployee(employeeId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable Long id) {
        Notification updated = notificationService.markAsRead(id);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }
}
