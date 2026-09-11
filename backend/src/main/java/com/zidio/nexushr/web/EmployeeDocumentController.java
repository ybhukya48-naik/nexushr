package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.EmployeeDocument;
import com.zidio.nexushr.service.EmployeeDocumentService;
import com.zidio.nexushr.web.dto.EmployeeDocumentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeDocumentController {

    private final EmployeeDocumentService documentService;

    public EmployeeDocumentController(EmployeeDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/{employeeId}/documents")
    public ResponseEntity<EmployeeDocumentResponse> uploadDocument(
            @PathVariable Long employeeId,
            @RequestParam String documentName,
            @RequestParam String documentType,
            @RequestParam(required = false) String documentUrl,
            @RequestParam(required = false) String uploadedBy) {

        EmployeeDocument document = documentService.uploadDocument(
                employeeId,
                documentName,
                documentType,
                documentUrl,
                uploadedBy
        );

        return ResponseEntity.ok(toResponse(document));
    }

    @GetMapping("/{employeeId}/documents")
    public ResponseEntity<List<EmployeeDocumentResponse>> getDocuments(
            @PathVariable Long employeeId) {

        List<EmployeeDocumentResponse> response =
                documentService.getDocuments(employeeId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<EmployeeDocumentResponse> getDocument(
            @PathVariable Long documentId) {

        return ResponseEntity.ok(
                toResponse(documentService.getDocument(documentId))
        );
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long documentId) {

        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    private EmployeeDocumentResponse toResponse(EmployeeDocument document) {

        EmployeeDocumentResponse response = new EmployeeDocumentResponse();

        response.setId(document.getId());
        response.setEmployeeId(document.getEmployee().getId());
        response.setDocumentName(document.getDocumentName());
        response.setDocumentType(document.getDocumentType());
        response.setDocumentUrl(document.getDocumentUrl());
        response.setUploadedBy(document.getUploadedBy());
        response.setUploadedAt(document.getUploadedAt());

        return response;
    }
}
