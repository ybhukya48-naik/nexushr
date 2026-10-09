package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.domain.PayrollStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<PayrollRecord, Long> {

    boolean existsByEmployee_IdAndPayMonth(Long employeeId, String payMonth);

    @EntityGraph(attributePaths = {"employee", "employee.company"})
    Optional<PayrollRecord> findByEmployee_IdAndPayMonth(Long employeeId, String payMonth);

    @Override
    @EntityGraph(attributePaths = {"employee", "employee.company"})
    List<PayrollRecord> findAll();

    @Override
    @EntityGraph(attributePaths = {"employee", "employee.company"})
    Optional<PayrollRecord> findById(Long id);

    @EntityGraph(attributePaths = {"employee", "employee.company"})
    List<PayrollRecord> findByEmployee_IdOrderByPayMonthDesc(Long employeeId);

    long countByStatus(PayrollStatus status);
}
