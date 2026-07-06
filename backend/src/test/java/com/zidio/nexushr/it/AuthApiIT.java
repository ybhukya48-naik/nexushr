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
 * Layer 1 — Integration tests: Auth API against real PostgreSQL.
 * Verifies the login endpoint generates a real JWT and role is assigned correctly.
 */
class AuthApiIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void login_withAdminCredentials_returnsTokenAndAdminRole() {
        String body = """
                {"username": "admin", "password": "any-password"}
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

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
                {"username": "hr", "password": "pass"}
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void generatedToken_canAccessProtectedEndpoint() {
        // Login to get a token
        String loginBody = """
                {"username": "admin", "password": "password"}
                """;
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.exchange(
                baseUrl() + "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginBody, loginHeaders),
                Map.class
        );
        String token = (String) loginResponse.getBody().get("accessToken");

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
