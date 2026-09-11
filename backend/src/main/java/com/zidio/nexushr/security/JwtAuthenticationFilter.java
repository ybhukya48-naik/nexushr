package com.zidio.nexushr.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        System.err.println("JWT_HEADER_CHECK: path=" + request.getRequestURI() + " hasAuthorization=" + (authHeader != null) + " startsBearer=" + (authHeader != null && authHeader.startsWith("Bearer ")));
        System.err.println("JWT_HEADER_CHECK: path=" + request.getRequestURI() + " hasAuthorization=" + (authHeader != null) + " startsBearer=" + (authHeader != null && authHeader.startsWith("Bearer ")));

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtTokenService.parse(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            System.err.println(
                "JWT_AUTH_OK: path=" + request.getRequestURI()
                + " username=" + username
                + " role=" + role
                + " authorities=" + authentication.getAuthorities()
                + " authenticated=" + authentication.isAuthenticated()
            );
        } catch (Exception ex) {
            System.err.println("JWT_AUTH_ERROR: " + ex.getClass().getName() + " - " + ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
