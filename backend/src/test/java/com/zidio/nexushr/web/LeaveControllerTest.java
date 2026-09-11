package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.AuthorizationService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.AttendanceService;
import com.zidio.nexushr.service.LeaveService;
import com.zidio.nexushr.web.dto.LeaveBalanceResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeaveController.class)
@Import(SecurityConfig.class)
class LeaveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LeaveService leaveService;

    @MockitoBean
    private AttendanceService attendanceService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean(name = "authorizationService")
    private AuthorizationService authorizationService;

    @Test
    @WithMockUser(username = "employee@example.com", roles = "EMPLOYEE")
    void list_returnsLeaveRequests() throws Exception {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStatus(LeaveStatus.PENDING);

        when(attendanceService.findEmployeeIdByUsername(
                "employee@example.com"
        )).thenReturn(1L);

        when(leaveService.findByEmployee(1L))
                .thenReturn(List.of(lr));

        mockMvc.perform(get("/api/v1/leaves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "employee@example.com", roles = "EMPLOYEE")
    void create_returnsCreatedRequest() throws Exception {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(2L);
        lr.setStatus(LeaveStatus.PENDING);

        when(attendanceService.findEmployeeIdByUsername(
                "employee@example.com"
        )).thenReturn(1L);

        when(leaveService.create(any(LeaveRequest.class)))
                .thenReturn(lr);

        mockMvc.perform(post("/api/v1/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employee": {
                                    "id": 1
                                  },
                                  "startDate": "2026-09-10",
                                  "endDate": "2026-09-12",
                                  "reason": "Annual vacation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void manager_canApproveLeave() throws Exception {
        LeaveRequest approved = new LeaveRequest();
        approved.setId(1L);
        approved.setStatus(LeaveStatus.APPROVED);

        when(leaveService.updateStatus(
                eq(1L),
                eq(LeaveStatus.APPROVED)
        )).thenReturn(approved);

        mockMvc.perform(patch("/api/v1/leaves/1/status")
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void hr_canRejectLeave() throws Exception {
        LeaveRequest rejected = new LeaveRequest();
        rejected.setId(1L);
        rejected.setStatus(LeaveStatus.REJECTED);

        when(leaveService.updateStatus(
                eq(1L),
                eq(LeaveStatus.REJECTED)
        )).thenReturn(rejected);

        mockMvc.perform(patch("/api/v1/leaves/1/status")
                        .param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canApproveLeave() throws Exception {
        LeaveRequest approved = new LeaveRequest();
        approved.setId(1L);
        approved.setStatus(LeaveStatus.APPROVED);

        when(leaveService.updateStatus(
                eq(1L),
                eq(LeaveStatus.APPROVED)
        )).thenReturn(approved);

        mockMvc.perform(patch("/api/v1/leaves/1/status")
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employee_cannotApproveLeave() throws Exception {
        mockMvc.perform(patch("/api/v1/leaves/1/status")
                        .param("status", "APPROVED"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employee_cannotRejectLeave() throws Exception {
        mockMvc.perform(patch("/api/v1/leaves/1/status")
                        .param("status", "REJECTED"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee@example.com", roles = "EMPLOYEE")
    void employee_canViewOwnLeaveBalance() throws Exception {
        LeaveBalanceResponse balance =
                new LeaveBalanceResponse(1L, 24, 5, 19);

        when(attendanceService.findEmployeeIdByUsername(
                "employee@example.com"
        )).thenReturn(1L);

        when(leaveService.getLeaveBalance(1L))
                .thenReturn(balance);

        mockMvc.perform(get("/api/v1/leaves/balance/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.annualEntitlement").value(24))
                .andExpect(jsonPath("$.usedDays").value(5))
                .andExpect(jsonPath("$.remainingDays").value(19));
    }

    @Test
    @WithMockUser(username = "employee@example.com", roles = "EMPLOYEE")
    void employee_cannotViewAnotherEmployeeLeaveBalance() throws Exception {
        mockMvc.perform(get("/api/v1/leaves/balance/2"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager@example.com", roles = "MANAGER")
    void manager_canViewEmployeeLeaveBalance() throws Exception {
        when(authorizationService.isEmployeeSelfOrManagement(
                eq(2L), any()
        )).thenReturn(true);

        LeaveBalanceResponse balance =
                new LeaveBalanceResponse(2L, 24, 8, 16);

        when(leaveService.getLeaveBalance(2L))
                .thenReturn(balance);

        mockMvc.perform(get("/api/v1/leaves/balance/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(2))
                .andExpect(jsonPath("$.annualEntitlement").value(24))
                .andExpect(jsonPath("$.usedDays").value(8))
                .andExpect(jsonPath("$.remainingDays").value(16));
    }

    @Test
    @WithMockUser(username = "hr@example.com", roles = "HR")
    void hr_canViewEmployeeLeaveBalance() throws Exception {
        when(authorizationService.isEmployeeSelfOrManagement(
                eq(3L), any()
        )).thenReturn(true);

        LeaveBalanceResponse balance =
                new LeaveBalanceResponse(3L, 24, 10, 14);

        when(leaveService.getLeaveBalance(3L))
                .thenReturn(balance);

        mockMvc.perform(get("/api/v1/leaves/balance/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(3))
                .andExpect(jsonPath("$.annualEntitlement").value(24))
                .andExpect(jsonPath("$.usedDays").value(10))
                .andExpect(jsonPath("$.remainingDays").value(14));
    }
}
