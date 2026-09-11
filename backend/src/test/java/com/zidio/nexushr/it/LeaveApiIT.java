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
 * Layer 1 - Integration tests: Leave request lifecycle against real PostgreSQL.
 * Covers create -> list -> approve/reject flow and overlap validation.
 */
class LeaveApiIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    private HttpHeaders authHeaders;

    @BeforeEach
    void setUp() {
        String token = jwtTokenService.generate(
                "admin",
                Map.of("role", "ADMIN")
        );

        authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    /**
     * Creates a leave request for seed employee id=1.
     */
    private int createLeaveRequest(
            String startDate,
            String endDate) {

        String body = """
                {
                  "employee": {"id": 1},
                  "startDate": "%s",
                  "endDate": "%s",
                  "reason": "Annual leave"
                }
                """.formatted(startDate, endDate);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders),
                Map.class
        );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        return (Integer) response.getBody().get("id");
    }

    @Test
    void createLeaveRequest_persists() {
        int id = createLeaveRequest(
                "2026-08-01",
                "2026-08-05"
        );

        assertThat(id).isPositive();
    }

    @Test
    void listLeaveRequests_includesCreatedRequest() {
        createLeaveRequest(
                "2026-08-10",
                "2026-08-12"
        );

        ResponseEntity<Object[]> response = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Object[].class
        );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isNotEmpty();
    }

    @Test
    void approveLeave_changesStatusToApproved() {
        int id = createLeaveRequest(
                "2026-08-15",
                "2026-08-18"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves/" + id
                        + "/status?status=APPROVED",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .containsEntry("status", "APPROVED");
    }

    @Test
    void rejectLeave_changesStatusToRejected() {
        int id = createLeaveRequest(
                "2026-08-20",
                "2026-08-23"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves/" + id
                        + "/status?status=REJECTED",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .containsEntry("status", "REJECTED");
    }

    @Test
    void overlappingLeave_returnsConflict() {
        createLeaveRequest(
                "2026-08-25",
                "2026-08-30"
        );

        String overlappingBody = """
                {
                  "employee": {"id": 1},
                  "startDate": "2026-08-28",
                  "endDate": "2026-09-02",
                  "reason": "Overlapping leave"
                }
                """;

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves",
                HttpMethod.POST,
                new HttpEntity<>(overlappingBody, authHeaders),
                Map.class
        );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approvedLeave_isIncludedInLeaveBalance() {
        int id = createLeaveRequest(
                "2026-12-01",
                "2026-12-03"
        );

        ResponseEntity<Map> approvalResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves/" + id
                        + "/status?status=APPROVED",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(approvalResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> balanceResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves/balance/1",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(balanceResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(balanceResponse.getBody())
                .containsEntry("employeeId", 1)
                .containsEntry("annualEntitlement", 24)
                .containsEntry("usedDays", 3)
                .containsEntry("remainingDays", 21);
    }

    @Test
    void rejectedLeave_isNotIncludedInLeaveBalance() {
        int id = createLeaveRequest(
                "2026-12-10",
                "2026-12-14"
        );

        ResponseEntity<Map> rejectionResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves/" + id
                        + "/status?status=REJECTED",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(rejectionResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> balanceResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/leaves/balance/1",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(balanceResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(balanceResponse.getBody())
                .containsEntry("employeeId", 1)
                .containsEntry("annualEntitlement", 24)
                .containsEntry("usedDays", 0)
                .containsEntry("remainingDays", 24);
    }
}
