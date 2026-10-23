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
 * Layer 1 — Integration tests: AI attrition insight endpoint with real PostgreSQL.
 * Verifies the attrition risk calculation against seed performance reviews.
 */
class AiAttritionApiIT extends AbstractIntegrationTest {

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

    @Test
    void attrition_recentHighScore_returnsLowRisk() {
        int employeeId = createEmployee("AI78");
        createReview(employeeId, 78, "2025-12-20");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/ai/attrition/" + employeeId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("riskBand", "LOW");
        assertThat(response.getBody()).containsKey("attritionRisk");
        assertThat(response.getBody()).containsKey("recommendation");
    }

    @Test
    void attrition_noReviews_returnsDefaultRisk() {
        int employeeId = createEmployee("AI65");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/ai/attrition/" + employeeId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("riskBand", "LOW");
    }

    private int createEmployee(String code) {
        String body = """
                {
                  "employeeCode": "%s",
                  "fullName": "Attrition %s",
                  "email": "%s@zidio.com",
                  "password": "Attrition1",
                  "roleType": "EMPLOYEE",
                  "department": "Engineering",
                  "designation": "Developer",
                  "joiningDate": "2024-01-01",
                  "baseSalary": 90000.00,
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

    private void createReview(int employeeId, int score, String reviewDate) {
        String body = """
                {
                  "employee": {"id": %d},
                  "reviewYear": 2025,
                  "score": %d,
                  "feedback": "Recorded for the attrition check.",
                  "reviewDate": "%s"
                }
                """.formatted(employeeId, score, reviewDate);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/performance",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void attrition_withoutToken_returnsUnauthorized() {
        ResponseEntity<Object> response = restTemplate.getForEntity(
                baseUrl() + "/api/v1/ai/attrition/1",
                Object.class
        );
        assertThat(response.getStatusCode().value()).isBetween(400, 499);
    }
}
