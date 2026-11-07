package com.swiftdrop.auth.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.reset.dto.ForgotPasswordRequest;
import com.swiftdrop.auth.reset.dto.ForgotPasswordResponse;
import com.swiftdrop.auth.reset.dto.ResetPasswordRequest;
import com.swiftdrop.auth.reset.dto.ResetPasswordResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PasswordResetController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PasswordResetExceptionHandler.class)
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PasswordResetService passwordResetService;

    @Test
    @DisplayName("POST /api/v1/auth/password/forgot with valid email returns 202 Accepted")
    void forgotPassword_validEmail_returns202() throws Exception {
        when(passwordResetService.requestPasswordReset(eq("user@example.com"), any(), any()))
                .thenReturn(new ForgotPasswordResponse());

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("user@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(ForgotPasswordResponse.DEFAULT_MESSAGE));
    }

    @Test
    @DisplayName("POST /api/v1/auth/password/forgot with invalid email returns 400 Bad Request")
    void forgotPassword_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/password/reset with valid payload returns 200 OK")
    void resetPassword_validPayload_returns200() throws Exception {
        when(passwordResetService.resetPassword(eq("valid-tok"), eq("SecurePass123!"), any(), any()))
                .thenReturn(new ResetPasswordResponse());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("valid-tok", "SecurePass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(ResetPasswordResponse.DEFAULT_MESSAGE));
    }

    @Test
    @DisplayName("POST /api/v1/auth/password/reset with too short password returns 400 Bad Request")
    void resetPassword_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("valid-tok", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/password/reset with invalid token returns 400 INVALID_RESET_TOKEN")
    void resetPassword_invalidToken_returns400() throws Exception {
        when(passwordResetService.resetPassword(eq("invalid-tok"), eq("SecurePass123!"), any(), any()))
                .thenThrow(new InvalidResetTokenException("Invalid, expired, or already-used reset token."));

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("invalid-tok", "SecurePass123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid, expired, or already-used reset token."));
    }
}

