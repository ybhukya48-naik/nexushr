package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.PerformanceGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceGoalRepository
        extends JpaRepository<PerformanceGoal, Long> {

    List<PerformanceGoal> findByEmployee_Id(Long employeeId);
}
