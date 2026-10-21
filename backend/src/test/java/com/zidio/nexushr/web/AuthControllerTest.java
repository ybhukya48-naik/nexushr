
package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.web.dto.AuthDtos;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @Test
    void login_withAdminCredentials_returnsTokenAndAdminRole() throws Exception {

        when(jwtTokenService.generate(anyString(), anyMap()))
                .thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("admin", "any-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("mock-jwt-token"))
                .andExpect(jsonPath("$.role")
                        .value("ADMIN"))
                .andExpect(jsonPath("$.username")
                        .value("admin"));
    }

    @Test
    void login_withHrUsername_returnsHrRole() throws Exception {

        when(jwtTokenService.generate(anyString(), anyMap()))
                .thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("hr", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role")
                        .value("HR"));
    }

    @Test
    void login_withManagerUsername_returnsManagerRole() throws Exception {

        when(jwtTokenService.generate(anyString(), anyMap()))
                .thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("manager", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role")
                        .value("MANAGER"));
    }

    @Test
    void login_withEmployeeDemoUsername_returnsEmployeeRole() throws Exception {

        when(jwtTokenService.generate(anyString(), anyMap()))
                .thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("employee", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role")
                        .value("EMPLOYEE"));
    }

    @Test
    void login_withUnknownUsername_returnsUnauthorized() throws Exception {

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("john", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withBlankPassword_returnsBadRequest() throws Exception {

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("admin", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withNullPassword_returnsBadRequest() throws Exception {

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("admin", null);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPassword_forExistingEmployee_storesNewPassword() throws Exception {
        when(employeeRepository.updateEmployeePassword(eq("staff@gorlegroup.com"), anyString()))
                .thenReturn(1);

        mockMvc.perform(post("/api/v1/auth/create-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"staff@gorlegroup.com","password":"newpassword1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Password created. Sign in with this email and the new password."));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(employeeRepository).updateEmployeePassword(eq("staff@gorlegroup.com"), hash.capture());
        assertTrue(new BCryptPasswordEncoder().matches("newpassword1", hash.getValue()));
    }

    @Test
    void createPassword_whenAccountIsNotEmployee_returnsForbidden() throws Exception {
        when(employeeRepository.updateEmployeePassword(eq("hr@gorlegroup.com"), anyString()))
                .thenReturn(0);
        when(employeeRepository.findRoleTypesByEmail("hr@gorlegroup.com"))
                .thenReturn(List.of("HR"));

        mockMvc.perform(post("/api/v1/auth/create-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"hr@gorlegroup.com","password":"newpassword1"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPassword_whenEmailMissing_returnsNotFound() throws Exception {
        when(employeeRepository.updateEmployeePassword(eq("missing@gorlegroup.com"), anyString()))
                .thenReturn(0);
        when(employeeRepository.findRoleTypesByEmail("missing@gorlegroup.com"))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/v1/auth/create-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@gorlegroup.com","password":"newpassword1"}
                                """))
                .andExpect(status().isNotFound());
    }
}
