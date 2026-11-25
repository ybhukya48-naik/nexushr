package com.zidio.nexushr.it;

import com.zidio.nexushr.AbstractIntegrationTest;
import com.zidio.nexushr.domain.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer 1 — Integration tests: Auth API against real PostgreSQL.
 * Verifies the login endpoint generates a real JWT and role is assigned correctly.
 */
class AuthApiIT extends AbstractIntegrationTest {

    private static final AtomicInteger NEXT_CLIENT = new AtomicInteger(1);
    private String clientIp;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        clientIp = "192.0.2." + NEXT_CLIENT.getAndIncrement();
        testEmployee("AUTHADMIN", "auth.admin@example.com", RoleType.ADMIN, "Integration1");
        testEmployee("AUTHHR", "auth.hr@example.com", RoleType.HR, "Integration1");
    }

    private HttpHeaders loginHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        headers.set("X-Forwarded-For", clientIp);
        return headers;
    }

    @Test
    void login_withAdminCredentials_returnsTokenAndAdminRole() {
        String body = """
                {"username": "auth.admin@example.com", "password": "Integration1"}
                """;
        HttpHeaders headers = loginHeaders();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("role", "ADMIN");
        assertThat((String) response.getBody().get("accessToken")).isNotBlank();
    }

    @Test
    void login_withHrUsername_returnsHrRole() {
        String body = """
                {"username": "auth.hr@example.com", "password": "Integration1"}
                """;
        HttpHeaders headers = loginHeaders();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("role", "HR");
    }

    @Test
    void login_withBlankPassword_returnsBadRequest() {
        String body = """
                {"username": "admin", "password": ""}
                """;
        HttpHeaders headers = loginHeaders();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_withWrongPassword_returnsGenericUnauthorizedMessage() {
        String body = """
                {"username": "auth.admin@example.com", "password": "wrong-password"}
                """;
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, loginHeaders()),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", "Username or password might be incorrect.");
    }

    @Test
    void login_fromMobileClient_isForbidden() {
        String body = """
                {"username": "auth.admin@example.com", "password": "Integration1"}
                """;
        HttpHeaders headers = loginHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void generatedToken_canAccessProtectedEndpoint() {
        // Login to get a token
        String loginBody = """
                {"username": "auth.admin@example.com", "password": "Integration1"}
                """;
        HttpHeaders loginHeaders = loginHeaders();
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginBody, loginHeaders),
                Map.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        String token = (String) loginResponse.getBody().get("accessToken");
        assertThat(token).isNotBlank();

        // Use that token to access a protected endpoint
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        ResponseEntity<Object[]> protectedResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/employees",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Object[].class
        );
        assertThat(protectedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
