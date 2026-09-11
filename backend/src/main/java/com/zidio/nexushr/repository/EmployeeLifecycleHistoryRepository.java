package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.EmployeeLifecycleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeLifecycleHistoryRepository
        extends JpaRepository<EmployeeLifecycleHistory, Long> {

    List<EmployeeLifecycleHistory> findByEmployeeIdOrderByChangedAtDesc(Long employeeId);
}
