package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.AttendanceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceAuditRepository extends JpaRepository<AttendanceAuditLog, Long> {

    List<AttendanceAuditLog> findTop100ByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<AttendanceAuditLog> findTop200ByOrderByCreatedAtDesc();
}
