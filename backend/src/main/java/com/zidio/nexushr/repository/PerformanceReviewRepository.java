package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceReviewRepository
        extends JpaRepository<PerformanceReview, Long> {

    List<PerformanceReview> findByEmployee_Id(Long employeeId);

    List<PerformanceReview> findByEmployee_IdOrderByReviewDateAscIdAsc(
            Long employeeId);
}
