package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.GenderType;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.web.dto.AuthDtos;
import com.zidio.nexushr.web.dto.RegisterRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

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
    public ResponseEntity<?> login(
            @RequestBody AuthDtos.LoginRequest request) {

        if (request == null ||
                request.username() == null ||
                request.username().isBlank() ||
                request.password() == null ||
                request.password().isBlank()) {

            return ResponseEntity.badRequest().build();
        }

        String normalizedUsername = request.username().trim();

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

        try {
            List<Object[]> rows = employeeRepository.findLoginMatches(normalizedUsername);
            boolean sawInactive = false;
            for (Object[] row : rows) {
                String life = text(row, 5);
                if (!isActiveFlag(row.length > 4 ? row[4] : null)
                        || "OFFBOARDED".equalsIgnoreCase(life)
                        || "OFFBOARDING".equalsIgnoreCase(life)) {
                    sawInactive = true;
                    continue;
                }
                if (!passwordMatches(request.password(), text(row, 1))) {
                    continue;
                }

                String email = text(row, 0);
                if (email == null || email.isBlank()) {
                    email = normalizedUsername.toLowerCase();
                }
                String role = text(row, 3);
                if (role == null || role.isBlank()) {
                    role = RoleType.EMPLOYEE.name();
                }
                String name = text(row, 6);
                if (name == null || name.isBlank()) {
                    name = email;
                }

                String token = jwtTokenService.generate(email, Map.of("role", role));
                return ResponseEntity.ok(new AuthDtos.LoginResponse(token, role, name));
            }

            String message = rows.isEmpty()
                    ? "No employee uses that email or employee code."
                    : sawInactive
                    ? "This employee account is not active."
                    : "Password does not match. Sign in with the password you just created.";
            return ResponseEntity.status(401).body(Map.of("message", message));
        } catch (Exception ex) {
            log.error("LOGIN_FAILED", ex);
            return ResponseEntity.status(401).body(Map.of(
                    "message",
                    "Sign-in could not check this account. Use the employee email and the new password."
            ));
        }
    }

    private boolean passwordMatches(String rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, storedHash);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String text(Object[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return null;
        }
        String value = row[index].toString().trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean isActiveFlag(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean active) {
            return active;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString().trim();
        return !text.equalsIgnoreCase("false")
                && !text.equalsIgnoreCase("f")
                && !text.equals("0");
    }

    @PostMapping("/create-password")
    public ResponseEntity<?> createPassword(
            @RequestBody AuthDtos.CreatePasswordRequest request) {

        try {
            if (request == null ||
                    request.email() == null ||
                    request.email().isBlank() ||
                    request.password() == null ||
                    request.password().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Email and password are required"));
            }

            String email = request.email().trim().toLowerCase();
            String password = request.password();

            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Please enter a valid email address"));
            }

            if (password.length() < 8) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Password must contain at least 8 characters"));
            }

            int updated = employeeRepository.updateEmployeePassword(
                    email,
                    passwordEncoder.encode(password));
            if (updated > 0) {
                return ResponseEntity.ok(Map.of(
                        "message", "Password created. Sign in with this email and the new password.",
                        "email", email
                ));
            }

            if (employeeRepository.findRoleTypesByEmail(email).isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message",
                                "No employee account uses this email"));
            }

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "This account cannot create a password here. Sign in with the password already on the employee."));
        } catch (Exception ex) {
            log.error("CREATE_PASSWORD_FAILED", ex);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "Could not save the new password. Use the email and password already saved on the employee, or ask HR."));
        }
    }

    @PostMapping("/logout")
    public Map<String, String> logout() {
        return Map.of(
                "status", "ok",
                "attendance", "kept");
    }
}
