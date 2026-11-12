package com.swiftdrop.auth.admin;

import com.swiftdrop.auth.admin.dto.UserListPage;
import com.swiftdrop.auth.admin.dto.UserSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUserController.class)
@Import(AdminSecurityConfig.class)
class AdminUserSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/admin/users allows ADMIN role")
    void adminUsers_allowsAdmin() throws Exception {
        when(adminUserService.listUsers(any(), any(), anyInt(), anyInt()))
            .thenReturn(new UserListPage(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("GET /api/v1/admin/users returns 403 Forbidden for DISPATCHER role")
    void adminUsers_forbiddenForDispatcher() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "courier", roles = {"COURIER"})
    @DisplayName("GET /api/v1/admin/users returns 403 Forbidden for COURIER role")
    void adminUsers_forbiddenForCourier() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    @DisplayName("GET /api/v1/admin/users returns 403 Forbidden for CUSTOMER role")
    void adminUsers_forbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "merchant", roles = {"MERCHANT"})
    @DisplayName("GET /api/v1/admin/users returns 403 Forbidden for MERCHANT role")
    void adminUsers_forbiddenForMerchant() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/users returns 401 Unauthorized when unauthenticated")
    void adminUsers_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/v1/admin/users allows ADMIN role")
    void createStaffUser_allowsAdmin() throws Exception {
        UserSummary summary = new UserSummary(
            "u-1", "courier.kamal@swiftdrop.lk", "Kamal", "+94771234567", "COURIER", true, Instant.now(), null
        );
        when(adminUserService.createStaffUser(any())).thenReturn(summary);

        mockMvc.perform(post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "courier.kamal@swiftdrop.lk",
                      "password": "SecurePassword#2026",
                      "fullName": "Kamal Gunaratne",
                      "phoneNumber": "+94771234567",
                      "role": "COURIER"
                    }
                    """))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("POST /api/v1/admin/users returns 403 Forbidden for DISPATCHER role")
    void createStaffUser_forbiddenForDispatcher() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@swiftdrop.lk\",\"password\":\"Pass#123\",\"fullName\":\"Test\",\"phoneNumber\":\"+94771234567\",\"role\":\"COURIER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "courier", roles = {"COURIER"})
    @DisplayName("POST /api/v1/admin/users returns 403 Forbidden for COURIER role")
    void createStaffUser_forbiddenForCourier() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@swiftdrop.lk\",\"password\":\"Pass#123\",\"fullName\":\"Test\",\"phoneNumber\":\"+94771234567\",\"role\":\"COURIER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    @DisplayName("POST /api/v1/admin/users returns 403 Forbidden for CUSTOMER role")
    void createStaffUser_forbiddenForCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@swiftdrop.lk\",\"password\":\"Pass#123\",\"fullName\":\"Test\",\"phoneNumber\":\"+94771234567\",\"role\":\"COURIER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "merchant", roles = {"MERCHANT"})
    @DisplayName("POST /api/v1/admin/users returns 403 Forbidden for MERCHANT role")
    void createStaffUser_forbiddenForMerchant() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@swiftdrop.lk\",\"password\":\"Pass#123\",\"fullName\":\"Test\",\"phoneNumber\":\"+94771234567\",\"role\":\"COURIER\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/users returns 401 Unauthorized when unauthenticated")
    void createStaffUser_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@swiftdrop.lk\",\"password\":\"Pass#123\",\"fullName\":\"Test\",\"phoneNumber\":\"+94771234567\",\"role\":\"COURIER\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("PATCH /api/v1/admin/users/{id} allows ADMIN role")
    void updateUserStatus_allowsAdmin() throws Exception {
        UserSummary summary = new UserSummary(
            "u-1", "courier.kamal@swiftdrop.lk", "Kamal", "+94771234567", "COURIER", false, Instant.now(), Instant.now()
        );
        when(adminUserService.updateUserStatus(anyString(), anyBoolean())).thenReturn(summary);

        mockMvc.perform(patch("/api/v1/admin/users/u-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = {"DISPATCHER"})
    @DisplayName("PATCH /api/v1/admin/users/{id} returns 403 Forbidden for DISPATCHER role")
    void updateUserStatus_forbiddenForDispatcher() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/u-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "courier", roles = {"COURIER"})
    @DisplayName("PATCH /api/v1/admin/users/{id} returns 403 Forbidden for COURIER role")
    void updateUserStatus_forbiddenForCourier() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/u-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    @DisplayName("PATCH /api/v1/admin/users/{id} returns 403 Forbidden for CUSTOMER role")
    void updateUserStatus_forbiddenForCustomer() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/u-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "merchant", roles = {"MERCHANT"})
    @DisplayName("PATCH /api/v1/admin/users/{id} returns 403 Forbidden for MERCHANT role")
    void updateUserStatus_forbiddenForMerchant() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/u-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id} returns 401 Unauthorized when unauthenticated")
    void updateUserStatus_unauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/u-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
            .andExpect(status().isUnauthorized());
    }
}
