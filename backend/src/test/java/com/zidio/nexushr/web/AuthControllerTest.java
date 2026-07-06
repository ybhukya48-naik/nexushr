package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.web.dto.AuthDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Needed to satisfy JwtAuthenticationFilter AND AuthController constructor
    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    void login_withAdminCredentials_returnsTokenAndAdminRole() throws Exception {
        when(jwtTokenService.generate(anyString(), anyMap())).thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("admin", "any-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_withHrUsername_returnsHrRole() throws Exception {
        when(jwtTokenService.generate(anyString(), anyMap())).thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("hr", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HR"));
    }

    @Test
    void login_withManagerUsername_returnsManagerRole() throws Exception {
        when(jwtTokenService.generate(anyString(), anyMap())).thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("manager", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void login_withUnknownUsername_returnsEmployeeRole() throws Exception {
        when(jwtTokenService.generate(anyString(), anyMap())).thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("john", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void login_withBlankPassword_returnsBadRequest() throws Exception {
        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("admin", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withNullPassword_returnsBadRequest() throws Exception {
        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("admin", null);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
