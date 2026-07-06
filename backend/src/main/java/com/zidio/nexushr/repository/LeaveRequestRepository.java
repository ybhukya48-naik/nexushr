package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
}
