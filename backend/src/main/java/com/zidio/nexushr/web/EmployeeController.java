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
    public List<EmployeeResponse> list() {
        return employeeService.findAll()
                .stream()
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
        try {
            return EmployeeResponse.from(employeeService.completeOwnProfile(employeeId, fullName, phone));
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
        requireHr(authentication);
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

        requireHr(authentication);
        return EmployeeResponse.from(savePhoto(id, file));
    }

    @GetMapping("/{id}")
    public EmployeeResponse getById(@PathVariable Long id) {
        return EmployeeResponse.from(
                employeeService.findById(id)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(
            @RequestBody EmployeeRequest request) {

        Employee created = employeeService.create(request);

        return EmployeeResponse.from(created);
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(
            @PathVariable Long id,
            @RequestBody EmployeeRequest request,
            Authentication authentication) {

        requireHr(authentication);

        Employee updated = employeeService.update(id, request);

        return EmployeeResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            Authentication authentication) {

        requireHr(authentication);

        employeeService.delete(id);
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

    private void requireHr(Authentication authentication) {
        if (authentication == null
                || authentication.getAuthorities().stream()
                .noneMatch(authority ->
                        "ROLE_HR".equals(authority.getAuthority()))) {

            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only HR users can edit or delete employees"
            );
        }
    }
}
