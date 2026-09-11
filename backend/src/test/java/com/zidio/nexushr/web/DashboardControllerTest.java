package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.domain.PayrollStatus;
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

import java.util.List;

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
        when(employeeRepository.countByLifecycleStatus(EmployeeLifecycleStatus.ACTIVE))
                .thenReturn(7L);
        when(employeeRepository.countByLifecycleStatus(EmployeeLifecycleStatus.PENDING_ONBOARDING))
                .thenReturn(2L);
        when(employeeRepository.countByLifecycleStatus(EmployeeLifecycleStatus.OFFBOARDING))
                .thenReturn(1L);

        when(attendanceRepository.count()).thenReturn(50L);

        when(leaveRequestRepository.count()).thenReturn(5L);
        when(leaveRequestRepository.countByStatus(LeaveStatus.PENDING))
                .thenReturn(2L);

        when(payrollRepository.count()).thenReturn(20L);
        when(payrollRepository.countByStatus(PayrollStatus.PAID))
                .thenReturn(15L);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(10))
                .andExpect(jsonPath("$.activeEmployees").value(7))
                .andExpect(jsonPath("$.pendingOnboarding").value(2))
                .andExpect(jsonPath("$.pendingOffboarding").value(1))
                .andExpect(jsonPath("$.attendanceEvents").value(50))
                .andExpect(jsonPath("$.leaveRequests").value(5))
                .andExpect(jsonPath("$.pendingLeaveRequests").value(2))
                .andExpect(jsonPath("$.payrollRecords").value(20))
                .andExpect(jsonPath("$.paidPayrollRecords").value(15));
    }

    @Test
    @WithMockUser(roles = "HR")
    void analytics_returnsDepartmentAndRoleCounts() throws Exception {
        when(employeeRepository.countEmployeesByDepartment())
                .thenReturn(List.of(
                        new Object[]{"Engineering", 4L},
                        new Object[]{"Human Resources", 2L}
                ));

        when(employeeRepository.countEmployeesByRole())
                .thenReturn(List.of(
                        new Object[]{"ADMIN", 1L},
                        new Object[]{"EMPLOYEE", 3L},
                        new Object[]{"HR", 1L},
                        new Object[]{"MANAGER", 1L}
                ));

        mockMvc.perform(get("/api/v1/dashboard/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeesByDepartment.Engineering").value(4))
                .andExpect(jsonPath("$.employeesByDepartment['Human Resources']").value(2))
                .andExpect(jsonPath("$.employeesByRole.ADMIN").value(1))
                .andExpect(jsonPath("$.employeesByRole.EMPLOYEE").value(3))
                .andExpect(jsonPath("$.employeesByRole.HR").value(1))
                .andExpect(jsonPath("$.employeesByRole.MANAGER").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void summary_accessibleByAdmin() throws Exception {
        when(employeeRepository.count()).thenReturn(0L);
        when(employeeRepository.countByLifecycleStatus(EmployeeLifecycleStatus.ACTIVE))
                .thenReturn(0L);
        when(employeeRepository.countByLifecycleStatus(EmployeeLifecycleStatus.PENDING_ONBOARDING))
                .thenReturn(0L);
        when(employeeRepository.countByLifecycleStatus(EmployeeLifecycleStatus.OFFBOARDING))
                .thenReturn(0L);
        when(attendanceRepository.count()).thenReturn(0L);
        when(leaveRequestRepository.count()).thenReturn(0L);
        when(leaveRequestRepository.countByStatus(LeaveStatus.PENDING))
                .thenReturn(0L);
        when(payrollRepository.count()).thenReturn(0L);
        when(payrollRepository.countByStatus(PayrollStatus.PAID))
                .thenReturn(0L);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void summary_accessibleByManager() throws Exception {
        when(employeeRepository.count()).thenReturn(0L);
        when(employeeRepository.countByLifecycleStatus(EmployeeLifecycleStatus.ACTIVE))
                .thenReturn(0L);
        when(employeeRepository.countByLifecycleStatus(EmployeeLifecycleStatus.PENDING_ONBOARDING))
                .thenReturn(0L);
        when(employeeRepository.countByLifecycleStatus(EmployeeLifecycleStatus.OFFBOARDING))
                .thenReturn(0L);
        when(attendanceRepository.count()).thenReturn(0L);
        when(leaveRequestRepository.count()).thenReturn(0L);
        when(leaveRequestRepository.countByStatus(LeaveStatus.PENDING))
                .thenReturn(0L);
        when(payrollRepository.count()).thenReturn(0L);
        when(payrollRepository.countByStatus(PayrollStatus.PAID))
                .thenReturn(0L);

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
