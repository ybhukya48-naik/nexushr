package com.zidio.nexushr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple sliding-window rate limiter for the /api/v1/auth/login endpoint.
 *
 * Limits each client IP to {@link #MAX_REQUESTS} login attempts within
 * any {@link #WINDOW_MILLIS}-millisecond window. Uses a lock-free
 * ConcurrentHashMap with periodic cleanup to bound memory usage.
 *
 * For production, replace with a Redis-backed rate limiter
 * (e.g. Bucket4j + Redis) to support multi-instance deployments.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final int  MAX_REQUESTS  = 10;          // max login attempts per window
    static final long WINDOW_MILLIS = 60_000L;     // 1 minute sliding window

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    /** Per-IP attempt tracker: IP → first-attempt-timestamp + count */
    private final ConcurrentHashMap<String, long[]> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!LOGIN_PATH.equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        long now = Instant.now().toEpochMilli();

        long[] state = attempts.compute(ip, (key, prev) -> {
            if (prev == null || now - prev[0] > WINDOW_MILLIS) {
                // New window
                return new long[]{now, 1};
            }
            prev[1]++;
            return prev;
        });

        if (state[1] > MAX_REQUESTS) {
            long retryAfterSeconds = (WINDOW_MILLIS - (now - state[0])) / 1000 + 1;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"Too many login attempts. Please try again in " + retryAfterSeconds + " seconds.\"}"
            );
            return;
        }

        // Periodically evict stale entries to avoid unbounded growth
        if (attempts.size() > 10_000) {
            attempts.entrySet().removeIf(e -> now - e.getValue()[0] > WINDOW_MILLIS);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Respect X-Forwarded-For when behind a trusted proxy / k8s ingress
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
