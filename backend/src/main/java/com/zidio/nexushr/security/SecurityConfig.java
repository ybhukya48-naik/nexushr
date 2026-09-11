package com.zidio.nexushr.security;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final String corsAllowedOrigin;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @org.springframework.beans.factory.annotation.Value("${app.security.cors-allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5175,http://127.0.0.1:5175}")
            String corsAllowedOrigin) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsAllowedOrigin = corsAllowedOrigin;
    }

    /**
     * JwtAuthenticationFilter is managed by Spring Security below.
     * Disable Spring Boot's automatic servlet registration to prevent
     * the filter from running twice and losing the SecurityContext.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(auth -> auth

                // =========================================================
                // AUTHENTICATION
                // =========================================================

                .requestMatchers("/api/v1/auth/**").permitAll()


                // =========================================================
                // PUBLIC ENDPOINTS
                // =========================================================

                .requestMatchers("/", "/error").permitAll()
                .requestMatchers("/test-public").access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(false))


                // =========================================================
                // ACTUATOR
                // =========================================================

                .requestMatchers("/actuator/health/**").permitAll()


                // =========================================================
                // SWAGGER / OPENAPI
                // =========================================================

                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()

                .requestMatchers(
                    "/v3/api-docs/**"
                ).permitAll()


                // =========================================================
                // DASHBOARD
                // =========================================================

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/dashboard/**"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER"
                )


                // =========================================================
                // EMPLOYEE LIFECYCLE - ROLE SPECIFIC
                // =========================================================

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/employees/*/submit-onboarding"
                )
                .hasAnyRole("HR", "ADMIN", "MANAGER")

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/employees/*/approve"
                )
                .hasAnyRole("HR", "ADMIN")

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/employees/*/start-offboarding"
                )
                .hasAnyRole("HR", "ADMIN")

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/employees/*/complete-offboarding"
                )
                .hasAnyRole("HR", "ADMIN")

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/employees/*/lifecycle-history"
                )
                .hasAnyRole("HR", "ADMIN", "MANAGER")

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/employees/*/promote",
                    "/api/v1/employees/*/transfer",
                    "/api/v1/employees/*/resign"
                )
                .hasAnyRole("HR", "ADMIN", "MANAGER")


                // =========================================================
                // EMPLOYEE DOCUMENTS
                // =========================================================

                .requestMatchers(
                    "/api/v1/employees/*/documents",
                    "/api/v1/employees/documents/**"
                )
                .hasAnyRole("HR", "ADMIN", "MANAGER")


                // =========================================================
                // EMPLOYEES
                // =========================================================

                // Authenticated users can access only their own profile
                // through the dedicated /me endpoint.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/employees/me"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER",
                    "EMPLOYEE"
                )

                // Employee directory remains management-only.
                .requestMatchers(
                    "/api/v1/employees/**"
                )
                .hasAnyRole("HR", "ADMIN", "MANAGER")


                // =========================================================
                // LEAVE
                // =========================================================

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/leaves"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER",
                    "EMPLOYEE"
                )

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/leaves"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER",
                    "EMPLOYEE"
                )

                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/v1/leaves/*/status"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER"
                )

                // Employees may view their own leave balance.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/leaves/balance/me"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER",
                    "EMPLOYEE"
                )

                // Leave balance endpoint is available to all authenticated roles.
                // Ownership is enforced by @PreAuthorize on LeaveController:
                // employees can access only their own balance,
                // while HR / ADMIN / MANAGER can access any employee balance.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/leaves/balance/*"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER",
                    "EMPLOYEE"
                )


                // =========================================================
                // ATTENDANCE - ROLE SPECIFIC
                // =========================================================

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/attendance/check-in",
                    "/api/v1/attendance/check-out",
                    "/api/v1/attendance/biometric/check-in",
                    "/api/v1/attendance/biometric/check-out"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER",
                    "EMPLOYEE"
                )

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/attendance"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER"
                )

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/attendance",
                    "/api/v1/attendance/metrics"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER"
                )

                // Employees may access their own attendance history.
                // Ownership is enforced by @PreAuthorize on the controller.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/attendance/employee/*"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER",
                    "EMPLOYEE"
                )


                // =========================================================
                // PAYROLL
                // =========================================================

                // Employees may view their own payroll history/payslip.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/payroll/employee/*",
                    "/api/v1/payroll/*/payslip"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER",
                    "EMPLOYEE"
                )

                // Payroll generation and payment operations are management-only.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/payroll"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER"
                )

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/payroll/*/mark-paid"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER"
                )

                // Management can list all payroll records.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/payroll"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER"
                )


                // =========================================================
                // PERFORMANCE
                // =========================================================

                // Performance reads may be accessed by employees.
                // Method-level ownership checks prevent cross-employee access.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/performance/employee/*",
                    "/api/v1/performance/scorecard/*",
                    "/api/v1/performance/dashboard/*",
                    "/api/v1/performance/goals/employee/*",
                    "/api/v1/performance/feedback/employee/*",
                    "/api/v1/performance/feedback/reviewer/*"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER",
                    "EMPLOYEE"
                )

                // All remaining performance operations are management-only.
                .requestMatchers(
                    "/api/v1/performance/**"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER"
                )


                // =========================================================
                // AI
                // =========================================================

                .requestMatchers(
                    "/api/v1/ai/**"
                )
                .hasAnyRole(
                    "HR",
                    "ADMIN",
                    "MANAGER"
                )


                // =========================================================
                // EVERYTHING ELSE
                // =========================================================

                .anyRequest().authenticated()
            )

            // =============================================================
            // JWT FILTER
            // =============================================================

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }


    // =============================================================
    // CORS CONFIGURATION
    // =============================================================

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
            List.of(corsAllowedOrigin.split(","))
        );

        configuration.setAllowedMethods(
            List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
            )
        );

        configuration.setAllowedHeaders(
            List.of("*")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
            "/**",
            configuration
        );

        return source;
    }
}
