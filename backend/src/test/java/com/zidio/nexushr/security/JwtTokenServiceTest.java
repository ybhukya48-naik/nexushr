package com.zidio.nexushr.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    // Secret must be at least 32 bytes for HMAC-SHA256
    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes!";
    private static final long EXPIRATION_MINUTES = 60;

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, EXPIRATION_MINUTES);
    }

    @Test
    void generate_returnsNonNullToken() {
        String token = jwtTokenService.generate("user@example.com", Map.of("role", "ADMIN"));

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void parse_returnsCorrectSubject() {
        String token = jwtTokenService.generate("user@example.com", Map.of());

        Claims claims = jwtTokenService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("user@example.com");
    }

    @Test
    void parse_returnsCustomClaims() {
        String token = jwtTokenService.generate("user@example.com", Map.of("role", "HR_MANAGER"));

        Claims claims = jwtTokenService.parse(token);

        assertThat(claims.get("role", String.class)).isEqualTo("HR_MANAGER");
    }

    @Test
    void generate_producesDistinctTokensForDifferentSubjects() {
        String token1 = jwtTokenService.generate("alice@example.com", Map.of());
        String token2 = jwtTokenService.generate("bob@example.com", Map.of());

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void parse_throwsException_forTamperedToken() {
        String token = jwtTokenService.generate("user@example.com", Map.of());
        String tampered = token + "tampered";

        assertThatThrownBy(() -> jwtTokenService.parse(tampered))
                .isInstanceOf(Exception.class);
    }
}
