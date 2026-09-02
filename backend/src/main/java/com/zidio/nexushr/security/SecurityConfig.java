package com.zidio.nexushr.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthRateLimitFilter authRateLimitFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthRateLimitFilter authRateLimitFilter) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authRateLimitFilter = authRateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // JWT API → CSRF is not required
            .csrf(csrf -> csrf.disable())

            // Do not create HTTP sessions
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(auth -> auth

                // -------------------------------------------------
                // PUBLIC ENDPOINTS
                // -------------------------------------------------

                // Authentication
                .requestMatchers("/api/v1/auth/**").permitAll()

                // Swagger / OpenAPI
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/webjars/**"
                ).permitAll()

                // Actuator health
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**"
                ).permitAll()

                // Root and error paths must be accessible
                .requestMatchers("/", "/error").permitAll()

                // Test endpoint
                .requestMatchers("/test-public").permitAll()

                // -------------------------------------------------
                // PROTECTED ENDPOINTS
                // -------------------------------------------------

                .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/**")
                    .hasAnyRole("HR", "ADMIN", "MANAGER")

                // Everything else requires JWT
                .anyRequest().authenticated()
            )

            // Rate-limit login requests
            .addFilterBefore(
                authRateLimitFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            // JWT authentication
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
