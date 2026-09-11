package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleHistory;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.EmployeeLifecycleService;
import com.zidio.nexushr.web.dto.EmployeeLifecycleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeLifecycleController.class)
@Import(SecurityConfig.class)
class EmployeeLifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmployeeLifecycleService lifecycleService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    private Employee employee(
            Long id,
            EmployeeLifecycleStatus status,
            boolean active) {

        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeCode("EMP001");
        employee.setFullName("Alice");
        employee.setEmail("alice@example.com");
        employee.setDepartment("IT");
        employee.setDesignation("Software Engineer");
        employee.setBaseSalary(new BigDecimal("50000"));
        employee.setActive(active);
        employee.setLifecycleStatus(status);

        return employee;
    }

    private EmployeeLifecycleRequest request(
            String changedBy,
            String comments) {

        EmployeeLifecycleRequest request =
                new EmployeeLifecycleRequest();

        request.setChangedBy(changedBy);
        request.setComments(comments);

        return request;
    }

    @Test
    @WithMockUser(roles = "HR")
    void submitOnboarding_returnsUpdatedEmployee() throws Exception {

        Employee result = employee(
                1L,
                EmployeeLifecycleStatus.PENDING_APPROVAL,
                false
        );

        when(lifecycleService.submitOnboarding(
                eq(1L),
                eq("hr@example.com"),
                eq("Documents verified")
        )).thenReturn(result);

        mockMvc.perform(
                post("/api/v1/employees/1/submit-onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request(
                                        "hr@example.com",
                                        "Documents verified"
                                )
                        ))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.lifecycleStatus")
                        .value("PENDING_APPROVAL"));

        verify(lifecycleService).submitOnboarding(
                1L,
                "hr@example.com",
                "Documents verified"
        );
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void submitOnboarding_managerIsAllowed() throws Exception {

        Employee result = employee(
                1L,
                EmployeeLifecycleStatus.PENDING_APPROVAL,
                false
        );

        when(lifecycleService.submitOnboarding(
                anyLong(),
                any(),
                any()
        )).thenReturn(result);

        mockMvc.perform(
                post("/api/v1/employees/1/submit-onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("manager@example.com", "Submit")
                        ))
        )
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void submitOnboarding_employeeIsForbidden() throws Exception {

        mockMvc.perform(
                post("/api/v1/employees/1/submit-onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("employee@example.com", "Submit")
                        ))
        )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void approveOnboarding_returnsActiveEmployee() throws Exception {

        Employee result = employee(
                1L,
                EmployeeLifecycleStatus.ACTIVE,
                true
        );

        when(lifecycleService.approveOnboarding(
                eq(1L),
                eq("hr@example.com"),
                eq("Approved")
        )).thenReturn(result);

        mockMvc.perform(
                post("/api/v1/employees/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("hr@example.com", "Approved")
                        ))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approveOnboarding_adminIsAllowed() throws Exception {

        Employee result = employee(
                1L,
                EmployeeLifecycleStatus.ACTIVE,
                true
        );

        when(lifecycleService.approveOnboarding(
                anyLong(),
                any(),
                any()
        )).thenReturn(result);

        mockMvc.perform(
                post("/api/v1/employees/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("admin@example.com", "Approved")
                        ))
        )
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void approveOnboarding_managerIsForbidden() throws Exception {

        mockMvc.perform(
                post("/api/v1/employees/1/approve")
        )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void startOffboarding_returnsOffboardingEmployee() throws Exception {

        Employee result = employee(
                1L,
                EmployeeLifecycleStatus.OFFBOARDING,
                true
        );

        when(lifecycleService.startOffboarding(
                eq(1L),
                eq("hr@example.com"),
                eq("Exit process started")
        )).thenReturn(result);

        mockMvc.perform(
                post("/api/v1/employees/1/start-offboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request(
                                        "hr@example.com",
                                        "Exit process started"
                                )
                        ))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus")
                        .value("OFFBOARDING"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void startOffboarding_managerIsForbidden() throws Exception {

        mockMvc.perform(
                post("/api/v1/employees/1/start-offboarding")
        )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void completeOffboarding_returnsOffboardedEmployee() throws Exception {

        Employee result = employee(
                1L,
                EmployeeLifecycleStatus.OFFBOARDED,
                false
        );

        when(lifecycleService.completeOffboarding(
                eq(1L),
                eq("hr@example.com"),
                eq("Exit completed")
        )).thenReturn(result);

        mockMvc.perform(
                post("/api/v1/employees/1/complete-offboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request(
                                        "hr@example.com",
                                        "Exit completed"
                                )
                        ))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus")
                        .value("OFFBOARDED"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void completeOffboarding_managerIsForbidden() throws Exception {

        mockMvc.perform(
                post("/api/v1/employees/1/complete-offboarding")
        )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void promote_managerIsAllowed() throws Exception {

        Employee result = employee(
                1L,
                EmployeeLifecycleStatus.ACTIVE,
                true
        );
        result.setDesignation("Senior Engineer");
        result.setBaseSalary(new BigDecimal("70000"));

        EmployeeLifecycleRequest request =
                request("manager@example.com", "Promotion");

        request.setDesignation("Senior Engineer");
        request.setBaseSalary(new BigDecimal("70000"));

        when(lifecycleService.promote(
                eq(1L),
                eq("manager@example.com"),
                eq("Promotion"),
                eq("Senior Engineer"),
                eq(new BigDecimal("70000"))
        )).thenReturn(result);

        mockMvc.perform(
                post("/api/v1/employees/1/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designation")
                        .value("Senior Engineer"))
                .andExpect(jsonPath("$.baseSalary").value(70000));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void promote_employeeIsForbidden() throws Exception {

        mockMvc.perform(
                post("/api/v1/employees/1/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("employee@example.com", "Promotion")
                        ))
        )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void transfer_returnsTransferredEmployee() throws Exception {

        Employee result = employee(
                1L,
                EmployeeLifecycleStatus.ACTIVE,
                true
        );
        result.setDepartment("Finance");

        when(lifecycleService.transfer(
                eq(1L),
                eq("hr@example.com"),
                eq("Business requirement"),
                eq("Finance")
        )).thenReturn(result);

        EmployeeLifecycleRequest request =
                request(
                        "hr@example.com",
                        "Business requirement"
                );
        request.setDepartment("Finance");

        mockMvc.perform(
                post("/api/v1/employees/1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department")
                        .value("Finance"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void transfer_employeeIsForbidden() throws Exception {

        mockMvc.perform(
                post("/api/v1/employees/1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request(
                                        "employee@example.com",
                                        "Transfer"
                                )
                        ))
        )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void resign_managerIsAllowed() throws Exception {

        Employee result = employee(
                1L,
                EmployeeLifecycleStatus.OFFBOARDING,
                true
        );

        when(lifecycleService.resign(
                eq(1L),
                eq("manager@example.com"),
                eq("Resignation approved")
        )).thenReturn(result);

        mockMvc.perform(
                post("/api/v1/employees/1/resign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request(
                                        "manager@example.com",
                                        "Resignation approved"
                                )
                        ))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus")
                        .value("OFFBOARDING"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void resign_employeeIsForbidden() throws Exception {

        mockMvc.perform(
                post("/api/v1/employees/1/resign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request(
                                        "employee@example.com",
                                        "Resignation"
                                )
                        ))
        )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void lifecycleHistory_returnsHistory() throws Exception {

        Employee employee = employee(
                1L,
                EmployeeLifecycleStatus.ACTIVE,
                true
        );

        EmployeeLifecycleHistory history =
                new EmployeeLifecycleHistory();

        history.setEmployee(employee);
        history.setFromStatus(
                EmployeeLifecycleStatus.PENDING_APPROVAL
        );
        history.setToStatus(
                EmployeeLifecycleStatus.ACTIVE
        );
        history.setAction("APPROVE_ONBOARDING");
        history.setChangedBy("hr@example.com");
        history.setComments("Approved");
        history.setChangedAt(LocalDateTime.now());

        when(lifecycleService.getHistory(1L))
                .thenReturn(List.of(history));

        mockMvc.perform(
                get("/api/v1/employees/1/lifecycle-history")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].action")
                        .value("APPROVE_ONBOARDING"))
                .andExpect(jsonPath("$[0].changedBy")
                        .value("hr@example.com"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void lifecycleHistory_managerIsAllowed() throws Exception {

        when(lifecycleService.getHistory(1L))
                .thenReturn(List.of());

        mockMvc.perform(
                get("/api/v1/employees/1/lifecycle-history")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void lifecycleHistory_employeeIsForbidden() throws Exception {

        mockMvc.perform(
                get("/api/v1/employees/1/lifecycle-history")
        )
                .andExpect(status().isForbidden());
    }

    @Test
    void lifecycleEndpoint_withoutAuthentication_returnsUnauthorized() throws Exception {

        mockMvc.perform(
                post("/api/v1/employees/1/submit-onboarding")
        )
                .andExpect(status().is4xxClientError());
    }
}
