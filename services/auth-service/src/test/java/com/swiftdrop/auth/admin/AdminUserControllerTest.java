package com.swiftdrop.auth.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.admin.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminUserService adminUserService;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/admin/users returns 200 with paginated user list")
    void listUsers_returnsOk() throws Exception {
        UserSummary summary = new UserSummary(
            "u-1",
            "courier.kamal@swiftdrop.lk",
            "Kamal Gunaratne",
            "+94771234567",
            "COURIER",
            true,
            Instant.now(),
            Instant.now()
        );
        UserListPage page = new UserListPage(List.of(summary), 1, 1, 0, 20);

        when(adminUserService.listUsers(eq("COURIER"), eq(true), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users")
                .param("role", "COURIER")
                .param("enabled", "true")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].email").value("courier.kamal@swiftdrop.lk"))
            .andExpect(jsonPath("$.content[0].role").value("COURIER"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/v1/admin/users returns 201 when staff account created")
    void createStaffUser_returnsCreated() throws Exception {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "courier.kamal@swiftdrop.lk",
            "SecurePass#2026",
            "Kamal Gunaratne",
            "+94771234567",
            "COURIER"
        );
        UserSummary summary = new UserSummary(
            "u-1",
            "courier.kamal@swiftdrop.lk",
            "Kamal Gunaratne",
            "+94771234567",
            "COURIER",
            true,
            Instant.now(),
            null
        );

        when(adminUserService.createStaffUser(any())).thenReturn(summary);

        mockMvc.perform(post("/api/v1/admin/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("u-1"))
            .andExpect(jsonPath("$.email").value("courier.kamal@swiftdrop.lk"))
            .andExpect(jsonPath("$.role").value("COURIER"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/v1/admin/users returns 409 when email already exists")
    void createStaffUser_returns409_whenEmailExists() throws Exception {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "existing@swiftdrop.lk",
            "SecurePass#2026",
            "Kamal Gunaratne",
            "+94771234567",
            "COURIER"
        );

        when(adminUserService.createStaffUser(any()))
            .thenThrow(new EmailAlreadyExistsException("A user account with this email address already exists."));

        mockMvc.perform(post("/api/v1/admin/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("POST /api/v1/admin/users returns 400 when role is invalid")
    void createStaffUser_returns400_whenInvalidRole() throws Exception {
        CreateStaffUserRequest request = new CreateStaffUserRequest(
            "customer@swiftdrop.lk",
            "SecurePass#2026",
            "Customer User",
            "+94771234567",
            "CUSTOMER"
        );

        when(adminUserService.createStaffUser(any()))
            .thenThrow(new InvalidStaffRoleException("Role must be either COURIER or DISPATCHER"));

        mockMvc.perform(post("/api/v1/admin/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("PATCH /api/v1/admin/users/{id} returns 200 with updated status")
    void updateUserStatus_returnsOk() throws Exception {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest(false);
        UserSummary summary = new UserSummary(
            "u-1",
            "courier.kamal@swiftdrop.lk",
            "Kamal Gunaratne",
            "+94771234567",
            "COURIER",
            false,
            Instant.now(),
            Instant.now()
        );

        when(adminUserService.updateUserStatus(eq("u-1"), eq(false))).thenReturn(summary);

        mockMvc.perform(patch("/api/v1/admin/users/u-1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("u-1"))
            .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("PATCH /api/v1/admin/users/{id} returns 404 when user not found")
    void updateUserStatus_returns404_whenNotFound() throws Exception {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest(false);

        when(adminUserService.updateUserStatus(eq("u-99"), eq(false)))
            .thenThrow(new UserNotFoundException("User not found with id: u-99"));

        mockMvc.perform(patch("/api/v1/admin/users/u-99")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
