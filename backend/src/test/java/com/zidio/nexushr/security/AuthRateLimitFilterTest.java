package com.zidio.nexushr.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.web.dto.AuthDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AuthRateLimitFilter — tests the filter directly without Spring
 * context overhead, which is the correct approach for servlet filters.
 */
class AuthRateLimitFilterTest {

    private AuthRateLimitFilter filter;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        filter = new AuthRateLimitFilter();
        clearAttempts();
    }

    private void clearAttempts() throws Exception {
        Field attemptsField = AuthRateLimitFilter.class.getDeclaredField("attempts");
        attemptsField.setAccessible(true);
        ((java.util.concurrent.ConcurrentHashMap<?, ?>) attemptsField.get(filter)).clear();
    }

    private MockHttpServletResponse invokeFilter(String path, String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr(remoteAddr != null ? remoteAddr : "127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);
        return response;
    }

    @Test
    void login_withinLimit_passesThrough() throws Exception {
        MockHttpServletResponse response = invokeFilter("/api/v1/auth/login", "10.0.0.1");
        // Filter passes: chain was called, so response stays at default (200 from chain stub)
        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void login_exceedingLimit_returnsTooManyRequests() throws Exception {
        // Exhaust the bucket from the same IP
        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS; i++) {
            invokeFilter("/api/v1/auth/login", "10.0.0.2");
        }
        // 11th request should be blocked
        MockHttpServletResponse response = invokeFilter("/api/v1/auth/login", "10.0.0.2");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(response.getContentAsString()).contains("error");
    }

    @Test
    void differentIps_haveIndependentBuckets() throws Exception {
        // Exhaust IP A
        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS; i++) {
            invokeFilter("/api/v1/auth/login", "192.168.1.1");
        }
        // IP B should not be rate-limited
        MockHttpServletResponse response = invokeFilter("/api/v1/auth/login", "192.168.1.2");
        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void nonLoginPath_isNotChecked() throws Exception {
        // Call /api/v1/auth/refresh many times — filter should not block it
        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS + 5; i++) {
            MockHttpServletResponse response = invokeFilter("/api/v1/auth/refresh", "10.0.0.3");
            assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
    }

    @Test
    void xForwardedFor_isUsedAsClientIp() throws Exception {
        // Exhaust via X-Forwarded-For header
        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login");
            request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, new MockFilterChain());
        }
        // 11th from same forwarded IP
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}

