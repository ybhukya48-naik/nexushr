package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.AuthorizationService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.AttendanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AttendanceController.class)
@Import(SecurityConfig.class)
class AttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AttendanceService attendanceService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean(name = "authorizationService")
    private AuthorizationService authorizationService;

    @Test
    @WithMockUser(username = "employee@example.com", roles = "EMPLOYEE")
    void checkIn_returnsCreatedAttendance() throws Exception {

        AttendanceRecord record = new AttendanceRecord();
        record.setId(1L);
        record.setWorkMinutes(0);

        when(attendanceService.findEmployeeIdByUsername(
                "employee@example.com"))
                .thenReturn(100L);

        when(attendanceService.checkIn(100L))
                .thenReturn(record);

        mockMvc.perform(
                post("/api/v1/attendance/check-in")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.workMinutes").value(0));
    }

    @Test
    @WithMockUser(username = "employee@example.com", roles = "EMPLOYEE")
    void checkOut_returnsUpdatedAttendance() throws Exception {

        AttendanceRecord record = new AttendanceRecord();
        record.setId(1L);
        record.setWorkMinutes(480);

        when(attendanceService.findEmployeeIdByUsername(
                "employee@example.com"))
                .thenReturn(100L);

        when(attendanceService.checkOut(100L))
                .thenReturn(record);

        mockMvc.perform(
                post("/api/v1/attendance/check-out")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.workMinutes").value(480));
    }

    @Test
    @WithMockUser(username = "employee@example.com", roles = "EMPLOYEE")
    void biometricCheckIn_isAllowedForEmployee() throws Exception {

        AttendanceRecord record = new AttendanceRecord();
        record.setId(2L);

        when(attendanceService.findEmployeeIdByUsername(
                "employee@example.com"))
                .thenReturn(100L);

        when(attendanceService.biometricCheckIn(100L))
                .thenReturn(record);

        mockMvc.perform(
                post("/api/v1/attendance/biometric/check-in")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    @WithMockUser(username = "employee@example.com", roles = "EMPLOYEE")
    void biometricCheckOut_isAllowedForEmployee() throws Exception {

        AttendanceRecord record = new AttendanceRecord();
        record.setId(3L);

        when(attendanceService.findEmployeeIdByUsername(
                "employee@example.com"))
                .thenReturn(100L);

        when(attendanceService.biometricCheckOut(100L))
                .thenReturn(record);

        mockMvc.perform(
                post("/api/v1/attendance/biometric/check-out")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void manualCreate_isForbiddenForEmployee() throws Exception {

        mockMvc.perform(
                post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
        )
        .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void listByDate_isForbiddenForEmployee() throws Exception {

        mockMvc.perform(
                get("/api/v1/attendance")
                        .param("date", "2026-07-01")
        )
        .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void listByEmployee_isForbiddenForEmployee() throws Exception {

        when(authorizationService.isEmployeeSelfOrManagement(eq(100L), any()))
                .thenReturn(false);

        mockMvc.perform(
                get("/api/v1/attendance/employee/100")
        )
        .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void manualCreate_isAllowedForHr() throws Exception {

        AttendanceRecord record = new AttendanceRecord();
        record.setId(10L);
        record.setWorkMinutes(480);

        when(attendanceService.create(any(AttendanceRecord.class)))
                .thenReturn(record);

        mockMvc.perform(
                post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(roles = "HR")
    void listByDate_isAllowedForHr() throws Exception {

        AttendanceRecord record = new AttendanceRecord();
        record.setId(20L);

        when(attendanceService.listByDate(
                eq(LocalDate.of(2026, 7, 1))))
                .thenReturn(List.of(record));

        mockMvc.perform(
                get("/api/v1/attendance")
                        .param("date", "2026-07-01")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(20));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void listByEmployee_isAllowedForManager() throws Exception {

        when(authorizationService.isEmployeeSelfOrManagement(eq(100L), any()))
                .thenReturn(true);

        AttendanceRecord record = new AttendanceRecord();
        record.setId(30L);

        when(attendanceService.listByEmployee(anyLong()))
                .thenReturn(List.of(record));

        mockMvc.perform(
                get("/api/v1/attendance/employee/100")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(30));
    }
}
