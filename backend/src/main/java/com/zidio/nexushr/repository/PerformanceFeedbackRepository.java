package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.PerformanceFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceFeedbackRepository extends JpaRepository<PerformanceFeedback, Long> {

    List<PerformanceFeedback> findByEmployee_Id(Long employeeId);

    List<PerformanceFeedback> findByReviewer_Id(Long reviewerId);
}
