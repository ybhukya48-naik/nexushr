package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestRepository
        extends JpaRepository<LeaveRequest, Long> {

    @Query("""
        SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END
        FROM LeaveRequest l
        WHERE l.employee.id = :employeeId
          AND l.status IN :statuses
          AND l.startDate <= :endDate
          AND l.endDate >= :startDate
    """)
    boolean existsOverlappingLeave(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") Collection<LeaveStatus> statuses
    );

    List<LeaveRequest> findByEmployee_IdOrderByStartDateDesc(
            Long employeeId
    );

    List<LeaveRequest> findByEmployee_IdAndStatus(
            Long employeeId,
            LeaveStatus status
    );

    long countByStatus(LeaveStatus status);
}
