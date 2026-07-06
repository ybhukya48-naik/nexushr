package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.LeaveService;
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
    private JwtTokenService jwtTokenService;

    @Test
    @WithMockUser
    void list_returnsLeaveRequests() throws Exception {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStatus(LeaveStatus.PENDING);
        when(leaveService.findAll()).thenReturn(List.of(lr));

        mockMvc.perform(get("/api/v1/leaves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser
    void create_returnsCreatedRequest() throws Exception {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(2L);
        lr.setStatus(LeaveStatus.PENDING);
        when(leaveService.create(any(LeaveRequest.class))).thenReturn(lr);

        mockMvc.perform(post("/api/v1/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    @WithMockUser
    void updateStatus_approvesLeave() throws Exception {
        LeaveRequest approved = new LeaveRequest();
        approved.setId(1L);
        approved.setStatus(LeaveStatus.APPROVED);
        when(leaveService.updateStatus(eq(1L), eq(LeaveStatus.APPROVED))).thenReturn(approved);

        mockMvc.perform(patch("/api/v1/leaves/1/status")
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @WithMockUser
    void updateStatus_rejectsLeave() throws Exception {
        LeaveRequest rejected = new LeaveRequest();
        rejected.setId(1L);
        rejected.setStatus(LeaveStatus.REJECTED);
        when(leaveService.updateStatus(eq(1L), eq(LeaveStatus.REJECTED))).thenReturn(rejected);

        mockMvc.perform(patch("/api/v1/leaves/1/status")
                        .param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
