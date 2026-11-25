package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.domain.PayrollStatus;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.LeaveRequestRepository;
import com.zidio.nexushr.repository.PayrollRepository;
import com.zidio.nexushr.security.AuthorizationService;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManagerTeamController.class)
@Import(SecurityConfig.class)
class ManagerTeamControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private EmployeeService employees;
    @MockitoBean(name = "authorizationService") private AuthorizationService authorization;
    @MockitoBean private JwtTokenService jwt;
    @MockitoBean private AttendanceRepository attendance;
    @MockitoBean private LeaveRequestRepository leaves;
    @MockitoBean private PayrollRepository payroll;

    @Test
    @WithMockUser(roles = "MANAGER")
    void reportIncludesOnlyAuthorizedReportsAndNoSalaryOrLeaveReason() throws Exception {
        Employee visible = new Employee();
        visible.setId(2L);
        visible.setEmployeeCode("E2");
        visible.setFullName("Team member");
        Employee hidden = new Employee();
        hidden.setId(3L);
        when(authorization.authenticatedEmployeeId(any())).thenReturn(1L);
        when(employees.findDirectReports(1L)).thenReturn(List.of(visible, hidden));
        when(authorization.canSeeEmployee(eq(visible), any())).thenReturn(true);
        AttendanceRecord day = new AttendanceRecord();
        day.setAttendanceDate(LocalDate.of(2026, 1, 5));
        day.setCheckInTime(LocalDateTime.of(2026, 1, 5, 9, 0));
        day.setCheckOutTime(LocalDateTime.of(2026, 1, 5, 18, 0));
        day.setManualLunch(true);
        day.setWorkMinutes(480);
        day.setOvertimeMinutes(60);
        when(attendance.findByEmployee_IdAndAttendanceDateBetween(2L, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31))).thenReturn(List.of(day));
        LeaveRequest leave = new LeaveRequest();
        leave.setStartDate(LocalDate.of(2026, 1, 9));
        leave.setEndDate(LocalDate.of(2026, 1, 9));
        leave.setReason("Private reason");
        leave.setStatus(LeaveStatus.PENDING);
        when(leaves.findByEmployee_IdOrderByStartDateDesc(2L)).thenReturn(List.of(leave));
        PayrollRecord record = new PayrollRecord();
        record.setStatus(PayrollStatus.PAID);
        when(payroll.findByEmployee_IdAndPayMonth(2L, "2026-01")).thenReturn(Optional.of(record));
        mvc.perform(get("/api/v1/manager/team-report").param("month", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].id").value(2))
                .andExpect(jsonPath("$.members[0].workedMinutes").value(480))
                .andExpect(jsonPath("$.members[0].overtimeMinutes").value(60))
                .andExpect(jsonPath("$.members[0].payrollStatus").value("PAID"))
                .andExpect(jsonPath("$.members[0].leave[0].status").value("PENDING"))
                .andExpect(jsonPath("$.members[0].leave[0].reason").doesNotExist())
                .andExpect(jsonPath("$.members[0].salary").doesNotExist())
                .andExpect(jsonPath("$.members[0].ctc").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void rejectsInvalidMonthAndBlocksNotificationsBroadcast() throws Exception {
        mvc.perform(get("/api/v1/manager/team-report").param("month", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/notifications/announcement/broadcast"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotAccessManagerReport() throws Exception {
        mvc.perform(get("/api/v1/manager/team-report").param("month", "2026-01"))
                .andExpect(status().isForbidden());
    }
}
