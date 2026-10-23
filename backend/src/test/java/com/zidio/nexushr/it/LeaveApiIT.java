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

    private static final String ACTOR_CODE = "LVACTOR";

    private static boolean actorReady;

    @BeforeEach
    void setUp() {
        HttpHeaders bootstrap = new HttpHeaders();
        bootstrap.setBearerAuth(jwtTokenService.generate(
                "bootstrap",
                Map.of("role", "ADMIN")
        ));
        bootstrap.setContentType(MediaType.APPLICATION_JSON);
        ensureActor(bootstrap);

        authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(jwtTokenService.generate(
                ACTOR_CODE,
                Map.of("role", "ADMIN")
        ));
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    /**
     * Leave creation looks up the signed-in user by employee code.
     * The demo admin row is gone, so the token subject has to be a real employee.
     */
    private void ensureActor(HttpHeaders bootstrap) {
        if (actorReady) {
            return;
        }

        String body = """
                {
                  "employeeCode": "%s",
                  "fullName": "Leave Actor",
                  "email": "lvactor@zidio.com",
                  "password": "LeaveTest1",
                  "roleType": "ADMIN",
                  "department": "QA",
                  "designation": "Tester",
                  "joiningDate": "2026-01-01",
                  "baseSalary": 50000.00,
                  "active": true
                }
                """.formatted(ACTOR_CODE);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/employees",
                HttpMethod.POST,
                new HttpEntity<>(body, bootstrap),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        actorReady = true;
    }

    private static int nextEmployee = 1;

    /**
     * Demo employees are removed by a later migration, so each test
     * creates the employee it uses.
     */
    private int createEmployee() {
        String code = "LV" + nextEmployee++;
        String body = """
                {
                  "employeeCode": "%s",
                  "fullName": "Leave Test %s",
                  "email": "%s@zidio.com",
                  "password": "LeaveTest1",
                  "roleType": "EMPLOYEE",
                  "department": "QA",
                  "designation": "Tester",
                  "joiningDate": "2026-01-01",
                  "baseSalary": 50000.00,
                  "active": true
                }
                """.formatted(code, code, code.toLowerCase());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/employees",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).intValue();
    }

    private int createLeaveRequest(
            int employeeId,
            String startDate,
            String endDate) {

        String body = """
                {
                  "employee": {"id": %d},
                  "startDate": "%s",
                  "endDate": "%s",
                  "reason": "Annual leave"
                }
                """.formatted(employeeId, startDate, endDate);

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
                createEmployee(),
                "2026-08-01",
                "2026-08-05"
        );

        assertThat(id).isPositive();
    }

    @Test
    void listLeaveRequests_includesCreatedRequest() {
        createLeaveRequest(
                createEmployee(),
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
                createEmployee(),
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
                createEmployee(),
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
        int employeeId = createEmployee();
        createLeaveRequest(
                employeeId,
                "2026-08-25",
                "2026-08-30"
        );

        String overlappingBody = """
                {
                  "employee": {"id": %d},
                  "startDate": "2026-08-28",
                  "endDate": "2026-09-02",
                  "reason": "Overlapping leave"
                }
                """.formatted(employeeId);

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
        int employeeId = createEmployee();
        int id = createLeaveRequest(
                employeeId,
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
                baseUrl() + "/api/v1/leaves/balance/" + employeeId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(balanceResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(balanceResponse.getBody())
                .containsEntry("employeeId", employeeId)
                .containsEntry("annualEntitlement", 24)
                .containsEntry("usedDays", 3)
                .containsEntry("remainingDays", 21);
    }

    @Test
    void rejectedLeave_isNotIncludedInLeaveBalance() {
        int employeeId = createEmployee();
        int id = createLeaveRequest(
                employeeId,
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
                baseUrl() + "/api/v1/leaves/balance/" + employeeId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(balanceResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(balanceResponse.getBody())
                .containsEntry("employeeId", employeeId)
                .containsEntry("annualEntitlement", 24)
                .containsEntry("usedDays", 0)
                .containsEntry("remainingDays", 24);
    }
}
