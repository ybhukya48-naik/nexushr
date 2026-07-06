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
    }

    @Test
    void attrition_seedEmployee2_returnsLowRisk() {
        // Seed employee id=2 (Sara Khan) has reviews: score 78 (2025) and 72 (2024)
        // Most recent score = 78 → in [75,∞) → baseRisk = 0.25 - 0.10 = 0.15 → LOW
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/ai/attrition/2",
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
    void attrition_seedEmployee1_noReviews_returnsDefaultRisk() {
        // Seed employee id=1 (Aarav Sharma) has no performance reviews
        // → default score 65 → baseRisk 0.25 + 0.20 = 0.45 → LOW
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/ai/attrition/1",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("riskBand", "LOW");
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
