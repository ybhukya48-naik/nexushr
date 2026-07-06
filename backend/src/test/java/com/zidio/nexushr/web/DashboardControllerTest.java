package com.zidio.nexushr.web;

import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.LeaveRequestRepository;
import com.zidio.nexushr.repository.PayrollRepository;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private AttendanceRepository attendanceRepository;

    @MockitoBean
    private LeaveRequestRepository leaveRequestRepository;

    @MockitoBean
    private PayrollRepository payrollRepository;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    @WithMockUser(roles = "HR")
    void summary_returnsAllCounts() throws Exception {
        when(employeeRepository.count()).thenReturn(10L);
        when(attendanceRepository.count()).thenReturn(50L);
        when(leaveRequestRepository.count()).thenReturn(5L);
        when(payrollRepository.count()).thenReturn(20L);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(10))
                .andExpect(jsonPath("$.attendanceEvents").value(50))
                .andExpect(jsonPath("$.leaveRequests").value(5))
                .andExpect(jsonPath("$.payrollRecords").value(20));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void summary_accessibleByAdmin() throws Exception {
        when(employeeRepository.count()).thenReturn(0L);
        when(attendanceRepository.count()).thenReturn(0L);
        when(leaveRequestRepository.count()).thenReturn(0L);
        when(payrollRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void summary_accessibleByManager() throws Exception {
        when(employeeRepository.count()).thenReturn(0L);
        when(attendanceRepository.count()).thenReturn(0L);
        when(leaveRequestRepository.count()).thenReturn(0L);
        when(payrollRepository.count()).thenReturn(0L);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void summary_forbiddenForEmployee() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    void summary_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().is4xxClientError());
    }
}
