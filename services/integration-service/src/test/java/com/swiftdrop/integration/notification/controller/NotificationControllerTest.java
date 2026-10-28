package com.swiftdrop.integration.notification.controller;

import com.swiftdrop.integration.notification.dto.NotificationDto;
import com.swiftdrop.integration.notification.dto.NotificationPageResponse;
import com.swiftdrop.integration.notification.service.NotificationNotFoundException;
import com.swiftdrop.integration.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {NotificationController.class, NotificationExceptionHandler.class})
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("GET /api/v1/notifications lists paginated notifications for caller")
    void getNotifications_returnsPaginatedList() throws Exception {
        NotificationDto dto = new NotificationDto(
                "notif-001",
                "Parcel Out for Delivery",
                "Your package SD-123 is out for delivery",
                "IN_APP",
                "pcl-1",
                "SD-123",
                false,
                Instant.parse("2026-10-28T10:00:00Z")
        );
        NotificationPageResponse pageResponse = new NotificationPageResponse(List.of(dto), 1L, 1L);

        when(notificationService.getNotifications(eq("cust-100"), eq(0), eq(20), eq(false)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(jwt().jwt(builder -> builder.subject("cust-100")))
                        .param("page", "0")
                        .param("size", "20")
                        .param("unreadOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value("notif-001"))
                .andExpect(jsonPath("$.items[0].title").value("Parcel Out for Delivery"))
                .andExpect(jsonPath("$.items[0].read").value(false));

        verify(notificationService).getNotifications("cust-100", 0, 20, false);
    }

    @Test
    @DisplayName("GET /api/v1/notifications with unreadOnly=true passes filter to service")
    void getNotifications_filtersUnreadOnly() throws Exception {
        NotificationPageResponse emptyResponse = new NotificationPageResponse(List.of(), 0L, 0L);

        when(notificationService.getNotifications(eq("cust-100"), anyInt(), anyInt(), eq(true)))
                .thenReturn(emptyResponse);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(jwt().jwt(builder -> builder.subject("cust-100")))
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.unreadCount").value(0));

        verify(notificationService).getNotifications("cust-100", 0, 20, true);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/read marks notification as read")
    void markNotificationRead_success() throws Exception {
        NotificationDto readDto = new NotificationDto(
                "notif-001",
                "Parcel Out for Delivery",
                "Your package SD-123 is out for delivery",
                "IN_APP",
                "pcl-1",
                "SD-123",
                true,
                Instant.parse("2026-10-28T10:00:00Z")
        );

        when(notificationService.markAsRead(eq("notif-001"), eq("cust-100")))
                .thenReturn(readDto);

        mockMvc.perform(post("/api/v1/notifications/notif-001/read")
                        .with(jwt().jwt(builder -> builder.subject("cust-100")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("notif-001"))
                .andExpect(jsonPath("$.read").value(true));

        verify(notificationService).markAsRead("notif-001", "cust-100");
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/read returns 404 when notification not found")
    void markNotificationRead_notFound() throws Exception {
        when(notificationService.markAsRead(eq("notif-missing"), eq("cust-100")))
                .thenThrow(new NotificationNotFoundException("Notification not found with id: notif-missing"));

        mockMvc.perform(post("/api/v1/notifications/notif-missing/read")
                        .with(jwt().jwt(builder -> builder.subject("cust-100")))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Notification not found with id: notif-missing"));
    }
}

