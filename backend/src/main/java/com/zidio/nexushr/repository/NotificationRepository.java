package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<Notification> findByEmployeeIdAndReadFalseOrderByCreatedAtDesc(Long employeeId);
    boolean existsByEmployeeIdAndNotificationTypeAndMessageContaining(Long employeeId, NotificationType notificationType, String messagePart);

    List<Notification> findByScheduledAtLessThanEqualAndDeliveryStatusOrderByScheduledAtAsc(
            LocalDateTime scheduledAt,
            com.zidio.nexushr.domain.NotificationDeliveryStatus deliveryStatus);
}
