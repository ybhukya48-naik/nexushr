package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Company;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.GenderType;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.CompanyRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.service.ExcelPayrollService;
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
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.demo-users-enabled:true}")
    private boolean demoUsersEnabled;

    public AuthController(
            JwtTokenService jwtTokenService,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder) {

        this.jwtTokenService = jwtTokenService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request) {

        if (request == null ||
                request.getFullName() == null ||
                request.getFullName().isBlank() ||
                request.getPassword() == null ||
                request.getPassword().isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Full name and password are required"));
        }

        String fullName = request.getFullName().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String phone = request.getPhone() == null ? "" : request.getPhone().trim();
        String phoneDigits = phoneKey(phone);
        String employeeCode = request.getEmployeeCode() == null
                ? ""
                : request.getEmployeeCode().trim();
        String password = request.getPassword();

        if (email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Enter your email address"));
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Please enter a valid email address"));
        }

        if (!phone.isBlank() && phoneDigits.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Enter a 10-digit phone number"));
        }

        if (password.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Password must contain at least 8 characters"));
        }

        Employee byCode = employeeCode.isBlank()
                ? null
                : employeeRepository.findByEmployeeCodeIgnoreCase(employeeCode).orElse(null);
        Employee byEmail = employeeRepository.findByEmailIgnoreCase(email).orElse(null);
        if (byCode != null && byEmail != null && !byCode.getId().equals(byEmail.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "That email belongs to a different employee code"));
        }
        Employee existing = byCode != null ? byCode : byEmail;
        if (existing != null) {
            return saveOwnCredentials(existing, fullName, email, phone, password);
        }

        if (!phoneDigits.isBlank() && employeeRepository.existsByPhoneDigits(phoneDigits)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "An account with this phone number already exists. Sign in with your email or employee code."));
        }

        String companyCode = request.getCompanyCode() == null
                ? ""
                : request.getCompanyCode().trim().toUpperCase();
        if (!companyCode.equals("CYOND") && !companyCode.equals("GORLE")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Choose CYOND or Gorle Group"));
        }
        Company company = companyRepository.findByCodeIgnoreCase(companyCode).orElse(null);
        if (company == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "That company is not set up yet"));
        }
        if (employeeCode.isBlank()) {
            employeeCode = generateEmployeeCode();
        } else if (employeeRepository.existsByEmployeeCode(employeeCode)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "That employee code is already used"));
        }

        Employee employee = new Employee();

        employee.setCompany(company);
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
                .body(profileBody(
                        "Profile created. Sign in with your email or employee code and this password.",
                        saved));
    }

    private ResponseEntity<?> saveOwnCredentials(
            Employee existing,
            String fullName,
            String email,
            String phone,
            String password) {
        if (isManagement(existing)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "This email belongs to an HR or admin account. Employees use their own email."));
        }
        if (!passwordUnset(existing)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "This profile already has a password. Sign in with your email or employee code."));
        }
        Employee emailOwner = employeeRepository.findByEmailIgnoreCase(email).orElse(null);
        if (emailOwner != null && existing.getId() != null && !existing.getId().equals(emailOwner.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "That email belongs to a different employee"));
        }
        existing.setFullName(fullName);
        existing.setEmail(email);
        existing.setPassword(passwordEncoder.encode(password));
        if (!phone.isBlank()) {
            existing.setPhone(phone.trim());
        }
        Employee saved = employeeRepository.save(existing);
        return ResponseEntity.ok(profileBody(
                "Password saved. Sign in with your email or employee code and this password.",
                saved));
    }

    private static Map<String, String> profileBody(String message, Employee employee) {
        return Map.of(
                "message", message,
                "employeeCode", employee.getEmployeeCode() == null ? "" : employee.getEmployeeCode(),
                "email", employee.getEmail() == null ? "" : employee.getEmail()
        );
    }

    private static boolean isManagement(Employee employee) {
        RoleType role = employee.getRoleType();
        return role == RoleType.ADMIN || role == RoleType.HR || role == RoleType.SUPER_ADMIN;
    }

    private static boolean passwordUnset(Employee employee) {
        String hash = employee.getPassword();
        return hash == null
                || hash.isBlank()
                || ExcelPayrollService.UNSET_PASSWORD_HASH.equals(hash);
    }

    private static String phoneKey(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits.length() == 10 ? digits : "";
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
        if (ExcelPayrollService.UNSET_PASSWORD_HASH.equals(storedHash)) {
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
                            "This email belongs to an HR or admin account. Employees create a password with their own email."));
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
