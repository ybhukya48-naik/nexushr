package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.PayrollRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRepository extends JpaRepository<PayrollRecord, Long> {
}
