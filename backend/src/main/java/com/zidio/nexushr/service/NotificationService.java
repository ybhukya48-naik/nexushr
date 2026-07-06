package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification createNotification(Long employeeId, String title, String message) {
        Notification notification = new Notification();
        notification.setEmployeeId(employeeId);
        notification.setTitle(title);
        notification.setMessage(message);
        return notificationRepository.save(notification);
    }

    public List<Notification> getNotificationsForEmployee(Long employeeId) {
        return notificationRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    public List<Notification> getUnreadNotificationsForEmployee(Long employeeId) {
        return notificationRepository.findByEmployeeIdAndReadFalseOrderByCreatedAtDesc(employeeId);
    }

    public Notification markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification != null) {
            notification.setRead(true);
            return notificationRepository.save(notification);
        }
        return null;
    }
}
