package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.security.JwtTokenService;
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

    @Test
    @WithMockUser
    void create_returnsCreatedRecord() throws Exception {
        AttendanceRecord record = new AttendanceRecord();
        record.setId(1L);
        record.setWorkMinutes(480);
        when(attendanceService.create(any(AttendanceRecord.class))).thenReturn(record);

        mockMvc.perform(post("/api/v1/attendance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.workMinutes").value(480));
    }

    @Test
    @WithMockUser
    void listByDate_returnsRecordsForDate() throws Exception {
        AttendanceRecord record = new AttendanceRecord();
        record.setId(2L);
        when(attendanceService.listByDate(eq(LocalDate.of(2026, 7, 1)))).thenReturn(List.of(record));

        mockMvc.perform(get("/api/v1/attendance")
                        .param("date", "2026-07-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));
    }

    @Test
    @WithMockUser
    void listByDate_returnsEmptyList_whenNoRecords() throws Exception {
        when(attendanceService.listByDate(any(LocalDate.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/attendance")
                        .param("date", "2025-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
