package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<Notification> findByEmployeeIdAndReadFalseOrderByCreatedAtDesc(Long employeeId);
}
