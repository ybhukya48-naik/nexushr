package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.service.EmployeeService;
import com.zidio.nexushr.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.zidio.nexushr.web.dto.EmployeeRequest;
import com.zidio.nexushr.web.dto.EmployeeResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AuthorizationService authorizationService;

    public EmployeeController(
            EmployeeService employeeService,
            AuthorizationService authorizationService) {
        this.employeeService = employeeService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<EmployeeResponse> list(Authentication authentication) {
        return employeeService.findAll()
                .stream()
                .filter(employee -> authorizationService.canSeeEmployee(employee, authentication))
                .map(EmployeeResponse::from)
                .toList();
    }

    @GetMapping("/me")
    public EmployeeResponse getCurrentEmployee(
            Authentication authentication) {

        Long employeeId =
                authorizationService.authenticatedEmployeeId(
                        authentication
                );

        if (employeeId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "This sign-in has no employee profile"
            );
        }

        return EmployeeResponse.from(
                employeeService.findById(employeeId)
        );
    }

    @PostMapping("/me/password")
    public EmployeeResponse changeOwnPassword(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long employeeId = requireSelf(authentication);
        String password = body == null ? null : body.get("password");
        try {
            return EmployeeResponse.from(employeeService.changeOwnPassword(employeeId, password));
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ex.getMessage()
            );
        }
    }

    @PostMapping("/me/profile")
    public EmployeeResponse completeOwnProfile(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long employeeId = requireSelf(authentication);
        String fullName = body == null ? null : body.get("fullName");
        String phone = body == null ? null : body.get("phone");
        String email = body == null ? null : body.get("email");
        try {
            return EmployeeResponse.from(employeeService.completeOwnProfile(employeeId, fullName, phone, email));
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ex.getMessage()
            );
        }
    }

    @PostMapping("/me/reset-password")
    public Map<String, String> resetOwnPassword(Authentication authentication) {
        Long employeeId = requireSelf(authentication);
        String temporary = employeeService.resetToDefaultPassword(employeeId);
        return Map.of(
                "message", "Password reset to the company default. Set a new password now.",
                "temporaryPassword", temporary
        );
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, String> resetPassword(
            @PathVariable Long id,
            Authentication authentication) {
        requireHrOrAdmin(authentication);
        Employee existing = employeeService.findById(id);
        if (!authorizationService.canSeeEmployee(existing, authentication)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Employee not found");
        }
        String temporary = employeeService.resetToDefaultPassword(id);
        return Map.of(
                "message", "Password reset. The employee must set a new password at the next sign-in.",
                "temporaryPassword", temporary
        );
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeResponse uploadMyPhoto(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        Long employeeId = requireSelf(authentication);
        return EmployeeResponse.from(savePhoto(employeeId, file));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        Employee employee = employeeService.findById(id);
        byte[] bytes = employee.getProfilePhoto();
        if (bytes == null || bytes.length == 0 || employee.getProfilePhotoType() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(employee.getProfilePhotoType()))
                .body(bytes);
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeResponse uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        requireHrOrAdmin(authentication);
        Employee existing = employeeService.findById(id);
        if (!authorizationService.canSeeEmployee(existing, authentication)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Employee not found");
        }
        return EmployeeResponse.from(savePhoto(id, file));
    }

    @GetMapping("/{id}")
    public EmployeeResponse getById(
            @PathVariable Long id,
            Authentication authentication) {
        Employee employee = employeeService.findById(id);
        if (!authorizationService.canSeeEmployee(employee, authentication)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Employee not found");
        }
        return EmployeeResponse.from(employee);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(
            @RequestBody EmployeeRequest request,
            Authentication authentication) {
        requireHrOrAdmin(authentication);
        if (!authorizationService.seesAllCompanies(authentication)) {
            String own = authorizationService.ownCompanyCode(authentication);
            if (own == null || own.isBlank()) {
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "This account has no company");
            }
            request.setCompanyCode(own);
        }

        Employee created = employeeService.create(request);

        return EmployeeResponse.from(created);
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(
            @PathVariable Long id,
            @RequestBody EmployeeRequest request,
            Authentication authentication) {

        requireHrOrAdmin(authentication);
        Employee existing = employeeService.findById(id);
        if (!authorizationService.canSeeEmployee(existing, authentication)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Employee not found");
        }
        if (!authorizationService.seesAllCompanies(authentication)) {
            request.setCompanyCode(existing.getCompanyCode());
        }

        Employee updated = employeeService.update(id, request);

        return EmployeeResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            Authentication authentication) {

        requireHrOrAdmin(authentication);
        Employee existing = employeeService.findById(id);
        if (!authorizationService.canSeeEmployee(existing, authentication)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Employee not found");
        }
        try {
            employeeService.delete(id);
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ex.getMessage()
            );
        }
    }

    private Long requireSelf(Authentication authentication) {
        Long employeeId = authorizationService.authenticatedEmployeeId(authentication);
        if (employeeId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated employee profile could not be resolved"
            );
        }
        return employeeId;
    }

    private Employee savePhoto(Long employeeId, MultipartFile file) throws IOException {
        try {
            return employeeService.saveProfilePhoto(
                    employeeId,
                    file == null ? null : file.getBytes(),
                    file == null ? null : file.getContentType()
            );
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ex.getMessage()
            );
        }
    }

    private void requireHrOrAdmin(Authentication authentication) {
        if (authentication == null
                || authentication.getAuthorities().stream()
                .noneMatch(authority ->
                        "ROLE_HR".equals(authority.getAuthority())
                                || "ROLE_ADMIN".equals(authority.getAuthority())
                                || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()))) {

            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only HR or admin can edit employees"
            );
        }
    }
}
