package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.service.EmployeeService;
import com.zidio.nexushr.security.AuthorizationService;
import org.springframework.security.core.Authentication;
import com.zidio.nexushr.web.dto.EmployeeRequest;
import com.zidio.nexushr.web.dto.EmployeeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authenticated employee profile could not be resolved"
            );
        }

        return EmployeeResponse.from(
                employeeService.findById(employeeId)
        );
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
