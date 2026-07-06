package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.PerformanceService;
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

@WebMvcTest(PerformanceController.class)
@Import(SecurityConfig.class)
class PerformanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PerformanceService performanceService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @Test
    @WithMockUser
    void list_returnsReviews() throws Exception {
        PerformanceReview review = new PerformanceReview();
        review.setId(1L);
        review.setScore(85);
        review.setReviewYear(2026);
        when(performanceService.findAll()).thenReturn(List.of(review));

        mockMvc.perform(get("/api/v1/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(85))
                .andExpect(jsonPath("$[0].reviewYear").value(2026));
    }

    @Test
    @WithMockUser
    void list_returnsEmptyArray_whenNoReviews() throws Exception {
        when(performanceService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser
    void create_returnsCreatedReview() throws Exception {
        PerformanceReview review = new PerformanceReview();
        review.setId(2L);
        review.setScore(90);
        when(performanceService.create(any(PerformanceReview.class))).thenReturn(review);

        mockMvc.perform(post("/api/v1/performance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.score").value(90));
    }
}
