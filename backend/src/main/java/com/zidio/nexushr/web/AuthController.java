package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.GenderType;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.web.dto.AuthDtos;
import com.zidio.nexushr.web.dto.RegisterRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenService jwtTokenService;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.demo-users-enabled:true}")
    private boolean demoUsersEnabled;

    public AuthController(
            JwtTokenService jwtTokenService,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder) {

        this.jwtTokenService = jwtTokenService;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request) {

        if (request == null ||
                request.getFullName() == null ||
                request.getFullName().isBlank() ||
                request.getEmail() == null ||
                request.getEmail().isBlank() ||
                request.getPassword() == null ||
                request.getPassword().isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Full name, email and password are required"));
        }

        String fullName = request.getFullName().trim();
        String email = request.getEmail().trim().toLowerCase();
        String password = request.getPassword();

        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Please enter a valid email address"));
        }

        if (password.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Password must contain at least 8 characters"));
        }

        if (employeeRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "An account with this email already exists"));
        }

        String employeeCode = generateEmployeeCode();

        Employee employee = new Employee();

        employee.setEmployeeCode(employeeCode);
        employee.setFullName(fullName);
        employee.setEmail(email);
        employee.setPhone(
                request.getPhone() == null || request.getPhone().isBlank()
                        ? null
                        : request.getPhone().trim()
        );

        // Never store a plain-text password.
        employee.setPassword(passwordEncoder.encode(password));

        // Public registration can only create normal employee accounts.
        employee.setRoleType(RoleType.EMPLOYEE);

        employee.setDepartment("General");
        employee.setDesignation("Employee");
        employee.setJoiningDate(LocalDate.now());
        employee.setBaseSalary(BigDecimal.ZERO);
        employee.setActive(true);
        employee.setGender(GenderType.PREFER_NOT_TO_SAY);
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);

        Employee saved = employeeRepository.save(employee);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Account created successfully",
                        "employeeCode", saved.getEmployeeCode(),
                        "email", saved.getEmail()
                ));
    }

    private String generateEmployeeCode() {

        String employeeCode;

        do {
            employeeCode = "E" +
                    String.format("%04d",
                            (int) (Math.random() * 10000));
        } while (employeeRepository.existsByEmployeeCode(employeeCode));

        return employeeCode;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(
            @RequestBody AuthDtos.LoginRequest request) {

        if (request == null ||
                request.username() == null ||
                request.username().isBlank() ||
                request.password() == null ||
                request.password().isBlank()) {

            return ResponseEntity.badRequest().build();
        }

        String normalizedUsername = request.username().trim();
        String normalizedEmail = normalizedUsername.toLowerCase();

        // Demo users are development/test accounts only.
        // Override DEMO_USERS_ENABLED=false in production for strict auth.
        if (demoUsersEnabled) {

            String demoRole = switch (normalizedUsername.toLowerCase()) {
                case "admin" -> "ADMIN";
                case "hr" -> "HR";
                case "manager" -> "MANAGER";
                case "employee" -> "EMPLOYEE";
                default -> null;
            };

            if (demoRole != null) {

                String token = jwtTokenService.generate(
                        normalizedUsername,
                        Map.of("role", demoRole)
                );

                return ResponseEntity.ok(
                        new AuthDtos.LoginResponse(
                                token,
                                demoRole,
                                normalizedUsername
                        )
                );
            }
        }

        // Real employee login using employee code or email.
        Optional<Employee> employeeOpt =
                employeeRepository.findByEmployeeCodeIgnoreCase(normalizedUsername);

        if (employeeOpt.isEmpty()) {
            employeeOpt =
                    employeeRepository.findByEmail(
                            normalizedEmail);
        }

        if (employeeOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Employee employee = employeeOpt.get();

        if (!employee.isActive() ||
                employee.getLifecycleStatus() != EmployeeLifecycleStatus.ACTIVE) {
            return ResponseEntity.status(401).build();
        }

        if (employee.getPassword() == null ||
                !passwordEncoder.matches(
                        request.password(),
                        employee.getPassword())) {

            return ResponseEntity.status(401).build();
        }

        String role = employee.getRoleType().name();

        String token = jwtTokenService.generate(
                employee.getEmail(),
                Map.of("role", role)
        );

        return ResponseEntity.ok(
                new AuthDtos.LoginResponse(
                        token,
                        role,
                        employee.getFullName()
                )
        );
    }
}
