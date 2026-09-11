package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Notification;
import com.zidio.nexushr.security.AuthorizationService;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import(com.zidio.nexushr.security.SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean(name = "authorizationService")
    private AuthorizationService authorizationService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @Test
    @WithMockUser(username = "employee@zidio.com", roles = "EMPLOYEE")
    void employeeCanReadOwnNotifications() throws Exception {

        when(authorizationService.isEmployeeSelfOrManagement(
                eq(1L), any()))
                .thenReturn(true);

        when(notificationService.getNotificationsForEmployee(1L))
                .thenReturn(List.of());

        mockMvc.perform(
                get("/api/v1/notifications/employee/1"))
                .andExpect(status().isOk());

        verify(notificationService)
                .getNotificationsForEmployee(1L);
    }

    @Test
    @WithMockUser(username = "employee@zidio.com", roles = "EMPLOYEE")
    void employeeCannotReadAnotherEmployeesNotifications()
            throws Exception {

        when(authorizationService.isEmployeeSelfOrManagement(
                eq(2L), any()))
                .thenReturn(false);

        mockMvc.perform(
                get("/api/v1/notifications/employee/2"))
                .andExpect(status().isForbidden());

        verify(notificationService, never())
                .getNotificationsForEmployee(2L);
    }

    @Test
    @WithMockUser(username = "hr@zidio.com", roles = "HR")
    void managementCanReadEmployeeNotifications() throws Exception {

        when(authorizationService.isEmployeeSelfOrManagement(
                eq(2L), any()))
                .thenReturn(true);

        when(notificationService.getNotificationsForEmployee(2L))
                .thenReturn(List.of());

        mockMvc.perform(
                get("/api/v1/notifications/employee/2"))
                .andExpect(status().isOk());

        verify(notificationService)
                .getNotificationsForEmployee(2L);
    }

    @Test
    @WithMockUser(username = "employee@zidio.com", roles = "EMPLOYEE")
    void employeeCannotSendNotification() throws Exception {

        mockMvc.perform(
                post("/api/v1/notifications/send")
                        .contentType("application/json")
                        .content("""
                            {
                              "employeeId": 2,
                              "title": "Test",
                              "message": "Test message",
                              "notificationType": "GENERAL",
                              "channel": "IN_APP"
                            }
                            """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notificationService);
    }

    @Test
    @WithMockUser(username = "hr@zidio.com", roles = "HR")
    void hrCanSendNotification() throws Exception {

        Notification notification = new Notification();
        notification.setEmployeeId(2L);
        notification.setTitle("Test");
        notification.setMessage("Test message");

        when(notificationService.sendNotification(
                anyLong(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(notification);

        mockMvc.perform(
                post("/api/v1/notifications/send")
                        .contentType("application/json")
                        .content("""
                            {
                              "employeeId": 2,
                              "title": "Test",
                              "message": "Test message",
                              "notificationType": "GENERAL",
                              "channel": "IN_APP"
                            }
                            """))
                .andExpect(status().isOk());

        verify(notificationService).sendNotification(
                eq(2L),
                eq("Test"),
                eq("Test message"),
                any(),
                any(),
                isNull(),
                isNull());
    }

    @Test
    @WithMockUser(username = "employee@zidio.com", roles = "EMPLOYEE")
    void employeeCanMarkOwnNotificationAsRead() throws Exception {

        when(authorizationService.canAccessNotification(
                eq(1L), any()))
                .thenReturn(true);

        Notification notification = new Notification();
        notification.setEmployeeId(1L);
        notification.setRead(true);

        when(notificationService.markAsRead(1L))
                .thenReturn(notification);

        mockMvc.perform(
                patch("/api/v1/notifications/1/read"))
                .andExpect(status().isOk());

        verify(notificationService).markAsRead(1L);
    }

    @Test
    @WithMockUser(username = "employee@zidio.com", roles = "EMPLOYEE")
    void employeeCannotMarkAnotherEmployeesNotificationAsRead()
            throws Exception {

        when(authorizationService.canAccessNotification(
                eq(2L), any()))
                .thenReturn(false);

        mockMvc.perform(
                patch("/api/v1/notifications/2/read"))
                .andExpect(status().isForbidden());

        verify(notificationService, never())
                .markAsRead(2L);
    }

    @Test
    @WithMockUser(username = "hr@zidio.com", roles = "HR")
    void managementCanMarkAnyNotificationAsRead() throws Exception {

        when(authorizationService.canAccessNotification(
                eq(2L), any()))
                .thenReturn(true);

        Notification notification = new Notification();
        notification.setEmployeeId(2L);
        notification.setRead(true);

        when(notificationService.markAsRead(2L))
                .thenReturn(notification);

        mockMvc.perform(
                patch("/api/v1/notifications/2/read"))
                .andExpect(status().isOk());

        verify(notificationService).markAsRead(2L);
    }
}
