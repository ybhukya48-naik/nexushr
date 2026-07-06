package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.PayrollService;
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

@WebMvcTest(PayrollController.class)
@Import(SecurityConfig.class)
class PayrollControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PayrollService payrollService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    @WithMockUser
    void list_returnsPayrollRecords() throws Exception {
        PayrollRecord record = new PayrollRecord();
        record.setId(1L);
        record.setPayMonth("2026-07");
        when(payrollService.findAll()).thenReturn(List.of(record));

        mockMvc.perform(get("/api/v1/payroll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payMonth").value("2026-07"));
    }

    @Test
    @WithMockUser
    void list_returnsEmptyArray_whenNoRecords() throws Exception {
        when(payrollService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/payroll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser
    void create_returnsCreatedRecord() throws Exception {
        PayrollRecord record = new PayrollRecord();
        record.setId(2L);
        record.setPayMonth("2026-07");
        when(payrollService.create(any(PayrollRecord.class))).thenReturn(record);

        mockMvc.perform(post("/api/v1/payroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }
}
