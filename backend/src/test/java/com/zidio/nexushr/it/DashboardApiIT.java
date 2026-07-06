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
 * Layer 1 — Integration tests: Dashboard summary API with real PostgreSQL.
 * Verifies role-based access control (HR/ADMIN/MANAGER allowed, EMPLOYEE denied).
 */
class DashboardApiIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    private HttpHeaders bearerHeaders(String role) {
        String token = jwtTokenService.generate(role.toLowerCase(), Map.of("role", role));
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    @Test
    void summary_withHrRole_returnsCountMap() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/dashboard/summary",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders("HR")),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys(
                "totalEmployees", "attendanceEvents", "leaveRequests", "payrollRecords");
        // Seed data has 3 employees
        assertThat((Integer) response.getBody().get("totalEmployees")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void summary_withAdminRole_returnsOk() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/dashboard/summary",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders("ADMIN")),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void summary_withManagerRole_returnsOk() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/dashboard/summary",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders("MANAGER")),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void summary_withEmployeeRole_returnsForbidden() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/dashboard/summary",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders("EMPLOYEE")),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void summary_withoutToken_returnsUnauthorized() {
        ResponseEntity<Object> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/dashboard/summary",
                Object.class
        );
        assertThat(response.getStatusCode().value()).isBetween(400, 499);
    }
}
