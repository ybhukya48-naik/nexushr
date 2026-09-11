package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeDocument;
import com.zidio.nexushr.repository.EmployeeDocumentRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmployeeDocumentService {

    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeDocumentService(
            EmployeeDocumentRepository documentRepository,
            EmployeeRepository employeeRepository) {
        this.documentRepository = documentRepository;
        this.employeeRepository = employeeRepository;
    }

    public EmployeeDocument uploadDocument(
            Long employeeId,
            String documentName,
            String documentType,
            String documentUrl,
            String uploadedBy) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Employee not found with id: " + employeeId));

        EmployeeDocument document = new EmployeeDocument();
        document.setEmployee(employee);
        document.setDocumentName(documentName);
        document.setDocumentType(documentType);
        document.setDocumentUrl(documentUrl);
        document.setUploadedBy(uploadedBy);
        document.setUploadedAt(LocalDateTime.now());

        return documentRepository.save(document);
    }

    public List<EmployeeDocument> getDocuments(Long employeeId) {

        employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Employee not found with id: " + employeeId));

        return documentRepository
                .findByEmployeeIdOrderByUploadedAtDesc(employeeId);
    }

    public EmployeeDocument getDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document not found with id: " + documentId));
    }

    public void deleteDocument(Long documentId) {

        if (!documentRepository.existsById(documentId)) {
            throw new RuntimeException(
                    "Document not found with id: " + documentId);
        }

        documentRepository.deleteById(documentId);
    }
}
