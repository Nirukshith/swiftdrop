package com.swiftdrop.integration;

import com.swiftdrop.integration.notification.controller.NotificationController;
import com.swiftdrop.integration.notification.dto.NotificationPageResponse;
import com.swiftdrop.integration.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import(SecurityConfig.class)
class IntegrationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/v1/notifications returns 401 Unauthorized when unauthenticated")
    void notifications_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/notifications succeeds when authenticated with valid CUSTOMER JWT")
    void notifications_authenticatedWithJwt() throws Exception {
        when(notificationService.getNotifications(anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new NotificationPageResponse(List.of(), 0L, 0L));

        mockMvc.perform(get("/api/v1/notifications")
                        .with(jwt().jwt(builder -> builder
                                .subject("customer-uuid-2")
                                .claim("email", "cust2@example.com")
                                .claim("role", "CUSTOMER")
                                .claim("roles", List.of("ROLE_CUSTOMER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/notifications rejects non-customer roles")
    void notifications_rejectsNonCustomerRole() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .with(jwt().jwt(builder -> builder
                                .subject("dispatcher-uuid-1")
                                .claim("roles", List.of("ROLE_DISPATCHER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER")))
                )
                .andExpect(status().isForbidden());
    }
}
