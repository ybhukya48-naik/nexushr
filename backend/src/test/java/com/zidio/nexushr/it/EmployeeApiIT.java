package com.zidio.nexushr.it;

import com.zidio.nexushr.AbstractIntegrationTest;
import com.zidio.nexushr.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer 1 — Integration tests: Employee API with a real PostgreSQL database.
 * Seed data from V2__seed_data.sql is present on startup (3 employees).
 */
class EmployeeApiIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    private HttpHeaders authHeaders;

    @BeforeEach
    void setUp() {
        // Use the 'admin' user (role ADMIN) — can access all endpoints
        String token = jwtTokenService.generate("admin", Map.of("role", "ADMIN"));
        authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void listEmployees_returnsSeedData() {
        HttpEntity<Void> request = new HttpEntity<>(authHeaders);
        ResponseEntity<Object[]> response = restTemplate.exchange(
                baseUrl() + "/api/v1/employees",
                HttpMethod.GET,
                request,
                Object[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // V2 seed inserts 3 employees
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void createEmployee_persistsAndReturnsEmployee() {
        String body = """
                {
                  "employeeCode": "IT001",
                  "fullName": "Integration Test User",
                  "email": "it.user@zidio.com",
                  "roleType": "EMPLOYEE",
                  "department": "QA",
                  "designation": "Test Engineer",
                  "joiningDate": "2026-07-04",
                  "baseSalary": 75000.00,
                  "active": true
                }
                """;
        HttpEntity<String> request = new HttpEntity<>(body, authHeaders);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/employees",
                HttpMethod.POST,
                request,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("fullName", "Integration Test User");
        assertThat(response.getBody()).containsEntry("email", "it.user@zidio.com");
        assertThat((Integer) response.getBody().get("id")).isPositive();
    }

    @Test
    void listEmployees_withoutToken_returnsUnauthorized() {
        ResponseEntity<Object> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/employees",
                Object.class
        );
        assertThat(response.getStatusCode().value()).isBetween(400, 499);
    }
}
