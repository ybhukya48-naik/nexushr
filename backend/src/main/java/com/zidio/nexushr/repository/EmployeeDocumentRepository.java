package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDocumentRepository
        extends JpaRepository<EmployeeDocument, Long> {

    List<EmployeeDocument> findByEmployeeIdOrderByUploadedAtDesc(Long employeeId);
}
