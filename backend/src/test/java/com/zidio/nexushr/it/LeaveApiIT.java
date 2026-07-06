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
 * Layer 1 — Integration tests: Leave request lifecycle against real PostgreSQL.
 * Covers create → list → approve/reject flow.
 */
class LeaveApiIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    private HttpHeaders authHeaders;

    @BeforeEach
    void setUp() {
        String token = jwtTokenService.generate("admin", Map.of("role", "ADMIN"));
        authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    /**
     * Creates a leave request for seed employee id=1 (Aarav Sharma).
     * Returns the created leave request id.
     */
    private int createLeaveRequest() {
        // Seed data employee ids start at 1; use employee 1 (Aarav Sharma)
        String body = """
                {
                  "employee": {"id": 1},
                  "startDate": "2026-08-01",
                  "endDate": "2026-08-05",
                  "reason": "Annual leave",
                  "status": "PENDING"
                }
                """;
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Integer) response.getBody().get("id");
    }

    @Test
    void createLeaveRequest_persists() {
        int id = createLeaveRequest();
        assertThat(id).isPositive();
    }

    @Test
    void listLeaveRequests_includesCreatedRequest() {
        createLeaveRequest();

        ResponseEntity<Object[]> response = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Object[].class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void approveLeave_changesStatusToApproved() {
        int id = createLeaveRequest();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves/" + id + "/status?status=APPROVED",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "APPROVED");
    }

    @Test
    void rejectLeave_changesStatusToRejected() {
        int id = createLeaveRequest();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves/" + id + "/status?status=REJECTED",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "REJECTED");
    }
}
