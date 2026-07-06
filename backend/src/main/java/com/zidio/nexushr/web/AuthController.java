package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.web.dto.AuthDtos;
import io.jsonwebtoken.Claims;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenService jwtTokenService;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtTokenService jwtTokenService,
                          EmployeeRepository employeeRepository,
                          PasswordEncoder passwordEncoder) {
        this.jwtTokenService = jwtTokenService;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(@RequestBody AuthDtos.LoginRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Employee> employeeOpt;
        
        // First try by employee code (for demo users like admin, hr, etc.)
        employeeOpt = employeeRepository.findByEmployeeCode(request.username());
        
        // If not found, try by email
        if (employeeOpt.isEmpty()) {
            employeeOpt = employeeRepository.findByEmail(request.username());
        }
        
        // For backward compatibility, support our demo role usernames
        if (employeeOpt.isEmpty()) {
            String role = switch (request.username()) {
                case "admin"   -> "ADMIN";
                case "hr"      -> "HR";
                case "manager" -> "MANAGER";
                default        -> "EMPLOYEE";
            };

            String token = jwtTokenService.generate(request.username(), Map.of("role", role));
            return ResponseEntity.ok(new AuthDtos.LoginResponse(token, role, request.username()));
        }

        Employee employee = employeeOpt.get();
        
        if (!passwordEncoder.matches(request.password(), employee.getPassword())) {
            return ResponseEntity.status(401).build();
        }

        String role = employee.getRoleType().name();
        String token = jwtTokenService.generate(employee.getEmail(), Map.of("role", role));
        return ResponseEntity.ok(new AuthDtos.LoginResponse(token, role, employee.getFullName()));
    }

    /**
     * Refresh endpoint: validates an existing (non-expired) token and issues a new one
     * with a fresh expiry. Requires the current token to be valid — the rate limiter
     * on /login does not apply here (the caller must already be authenticated).
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.LoginResponse> refresh(@RequestBody AuthDtos.RefreshRequest request) {
        try {
            Claims claims  = jwtTokenService.parse(request.accessToken());
            String subject = claims.getSubject();
            String role    = (String) claims.get("role");

            String newToken = jwtTokenService.generate(subject, Map.of("role", role));
            return ResponseEntity.ok(new AuthDtos.LoginResponse(newToken, role, subject));
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }
}
