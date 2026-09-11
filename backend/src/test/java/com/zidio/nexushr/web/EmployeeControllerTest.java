package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.security.AuthorizationService;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.EmployeeService;
import com.zidio.nexushr.web.dto.EmployeeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
    private AuthorizationService authorizationService;

    @Test
    @WithMockUser(roles = "HR")
    void list_returnsEmployeeList() throws Exception {

        Employee e = new Employee();
        e.setId(1L);
        e.setEmployeeCode("EMP001");
        e.setFullName("Alice");
        e.setEmail("alice@example.com");

        when(employeeService.findAll())
                .thenReturn(List.of(e));

        mockMvc.perform(
                get("/api/v1/employees")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Alice"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void list_returnsEmptyArray_whenNoEmployees() throws Exception {

        when(employeeService.findAll())
                .thenReturn(List.of());

        mockMvc.perform(
                get("/api/v1/employees")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "HR")
    void create_returnsCreatedEmployee() throws Exception {

        Employee e = new Employee();

        e.setId(2L);
        e.setEmployeeCode("EMP002");
        e.setFullName("Bob");
        e.setEmail("bob@example.com");

        when(employeeService.create(any(EmployeeRequest.class)))
                .thenReturn(e);

        EmployeeRequest request = new EmployeeRequest();

        request.setEmployeeCode("EMP002");
        request.setFullName("Bob");
        request.setEmail("bob@example.com");
        request.setPassword("Password@123");

        mockMvc.perform(
                post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.fullName").value("Bob"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void update_returnsUpdatedEmployee() throws Exception {

        Employee e = new Employee();

        e.setId(1L);
        e.setEmployeeCode("EMP001");
        e.setFullName("Alice Updated");
        e.setEmail("alice.updated@example.com");

        when(employeeService.update(
                any(Long.class),
                any(EmployeeRequest.class)
        )).thenReturn(e);

        EmployeeRequest request = new EmployeeRequest();

        request.setFullName("Alice Updated");
        request.setEmail("alice.updated@example.com");

        mockMvc.perform(
                put("/api/v1/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fullName").value("Alice Updated"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void getById_returnsEmployee() throws Exception {

        Employee e = new Employee();

        e.setId(1L);
        e.setEmployeeCode("EMP001");
        e.setFullName("Alice");
        e.setEmail("alice@example.com");

        when(employeeService.findById(1L))
                .thenReturn(e);

        mockMvc.perform(
                get("/api/v1/employees/1")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fullName").value("Alice"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void delete_returnsNoContent() throws Exception {

        mockMvc.perform(
                delete("/api/v1/employees/1")
        )
                .andExpect(status().isNoContent());
    }

    @Test
    void list_withoutAuth_returnsUnauthorized() throws Exception {

        mockMvc.perform(
                get("/api/v1/employees")
        )
                .andExpect(status().is4xxClientError());
    }
}
