package com.swiftdrop.auth.audit;

import com.swiftdrop.auth.audit.dto.AuditLogPageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminAuditController.class, AuditExceptionHandler.class})
@Import(AuditSecurityConfig.class)
class AdminAuditSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAuditService adminAuditService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/admin/audit allows ADMIN role")
    void adminAudit_allowsAdmin() throws Exception {
        when(adminAuditService.getAuditLogs(any(), any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new AuditLogPageResponse(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/api/v1/admin/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("GET /api/v1/admin/audit returns 403 Forbidden for DISPATCHER role (ADR 001)")
    void adminAudit_forbiddenForDispatcher() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "courier", roles = {"COURIER"})
    @DisplayName("GET /api/v1/admin/audit returns 403 Forbidden for COURIER role")
    void adminAudit_forbiddenForCourier() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    @DisplayName("GET /api/v1/admin/audit returns 403 Forbidden for CUSTOMER role")
    void adminAudit_forbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "merchant", roles = {"MERCHANT"})
    @DisplayName("GET /api/v1/admin/audit returns 403 Forbidden for MERCHANT role")
    void adminAudit_forbiddenForMerchant() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit returns 401 Unauthorized when unauthenticated")
    void adminAudit_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/admin/audit rejects invalid page index with 400 Bad Request")
    void adminAudit_rejectsNegativePage() throws Exception {
        when(adminAuditService.getAuditLogs(any(), any(), any(), any(), any(), anyInt(), anyInt()))
            .thenThrow(new IllegalArgumentException("Page index must not be negative"));

        mockMvc.perform(get("/api/v1/admin/audit?page=-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
