package com.swiftdrop.auth.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.client.ClientTokenService;
import com.swiftdrop.auth.internal.dto.ClientTokenRequest;
import com.swiftdrop.auth.internal.dto.ClientTokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OAuthTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
class OAuthTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClientTokenService clientTokenService;

    @Test
    @DisplayName("POST /api/v1/auth/token JSON body returns 200 with access token")
    void tokenEndpoint_jsonBody_succeeds() throws Exception {
        when(clientTokenService.exchangeToken(any(), any()))
                .thenReturn(new ClientTokenResponse("jwt-token-123", "Bearer", 900, "parcels:create"));

        ClientTokenRequest request = new ClientTokenRequest("client-id", "secret", "client_credentials", "parcels:create");

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("jwt-token-123"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.scope").value("parcels:create"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/token form-urlencoded body returns 200 with access token")
    void tokenEndpoint_formUrlEncoded_succeeds() throws Exception {
        when(clientTokenService.exchangeToken(any(), any()))
                .thenReturn(new ClientTokenResponse("jwt-token-456", "Bearer", 900, "parcels:read"));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "client-id")
                        .param("client_secret", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("jwt-token-456"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900));
    }

    @Test
    @DisplayName("POST /api/v1/auth/token with invalid credentials returns 401 Unauthorized")
    void tokenEndpoint_invalidCredentials_returns401() throws Exception {
        when(clientTokenService.exchangeToken(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid client credentials"));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClientTokenRequest("bad", "bad"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/token with disabled client returns 403 Forbidden")
    void tokenEndpoint_disabledClient_returns403() throws Exception {
        when(clientTokenService.exchangeToken(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Client is disabled or revoked"));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClientTokenRequest("disabled", "secret"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/auth/token with invalid scope returns 400 Bad Request")
    void tokenEndpoint_invalidScope_returns400() throws Exception {
        when(clientTokenService.exchangeToken(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested scope not authorized"));

        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClientTokenRequest("client", "secret", "client_credentials", "admin:all"))))
                .andExpect(status().isBadRequest());
    }
}
