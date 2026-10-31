package com.zidio.nexushr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Production browser gate. Employee and payroll data stay behind a login,
 * and a Chrome tab opened on the raw API host is refused. The Cyond site
 * calls /api on its own address, which the host proxies here.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CyondBrowserGateFilter extends OncePerRequestFilter {

    static final Set<String> ALLOWED_ORIGINS = Set.of(
            "https://hr.cyond.com",
            "https://cyond.com",
            "https://www.cyond.com"
    );

    private final boolean browserGate;

    public CyondBrowserGateFilter(
            @Value("${app.security.browser-gate:false}") boolean browserGate) {
        this.browserGate = browserGate;
    }

    static boolean permitted(String origin, String fetchSite, String fetchMode, String path) {
        if (path != null && (path.equals("/actuator/health") || path.startsWith("/actuator/health/"))) {
            return true;
        }
        if ("navigate".equalsIgnoreCase(fetchMode)) {
            return false;
        }
        if (origin == null || origin.isBlank()) {
            return fetchSite == null
                    || fetchSite.isBlank()
                    || "same-origin".equalsIgnoreCase(fetchSite)
                    || "same-site".equalsIgnoreCase(fetchSite);
        }
        return ALLOWED_ORIGINS.contains(origin.trim());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!browserGate || permitted(
                request.getHeader("Origin"),
                request.getHeader("Sec-Fetch-Site"),
                request.getHeader("Sec-Fetch-Mode"),
                request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"message\":\"This API only accepts the Cyond site. Employee and payroll details are not public.\"}");
    }
}
