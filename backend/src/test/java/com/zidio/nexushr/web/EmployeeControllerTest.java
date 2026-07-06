package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeController.class)
@Import(SecurityConfig.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmployeeService employeeService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    @WithMockUser
    void list_returnsEmployeeList() throws Exception {
        Employee e = new Employee();
        e.setId(1L);
        e.setFullName("Alice");
        when(employeeService.findAll()).thenReturn(List.of(e));

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Alice"));
    }

    @Test
    @WithMockUser
    void list_returnsEmptyArray_whenNoEmployees() throws Exception {
        when(employeeService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser
    void create_returnsCreatedEmployee() throws Exception {
        Employee e = new Employee();
        e.setId(2L);
        e.setFullName("Bob");
        when(employeeService.create(any(Employee.class))).thenReturn(e);

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.fullName").value("Bob"));
    }

    @Test
    void list_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().is4xxClientError());
    }
}
