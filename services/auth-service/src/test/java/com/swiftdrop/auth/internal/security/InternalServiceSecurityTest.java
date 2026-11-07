package com.swiftdrop.auth.internal.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.internal.dto.InternalCreateClientRequest;
import com.swiftdrop.auth.internal.dto.InternalCreateUserRequest;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import com.swiftdrop.auth.client.ClientCredentialRepository;
import com.swiftdrop.common.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for InternalServiceSecurityFilter.
 * Verifies that the filter correctly enforces authentication and least-privilege
 * access control on /internal/v1/** endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InternalServiceSecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientCredentialRepository clientCredentialRepository;

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String NOTIF_TOKEN = "swiftdrop-internal-notif-secret-2026";
    private static final String MERCHANT_TOKEN = "swiftdrop-internal-merchant-secret-2026";

    @BeforeEach
    void setUp() {
        clientCredentialRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Anonymous request to internal endpoints is rejected with 401")
    void anonymousRequest_isRejected() throws Exception {
        mockMvc.perform(get("/internal/v1/users/usr-1/contact"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid internal service token is rejected with 401")
    void invalidInternalToken_isRejected() throws Exception {
        mockMvc.perform(get("/internal/v1/users/usr-1/contact")
                        .header(INTERNAL_TOKEN_HEADER, "invalid-token-xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bearer JWT (not an internal token) cannot access internal endpoints")
    void userJwt_cannotAccessInternalEndpoints() throws Exception {
        String dummyJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.dummyPayload.dummySig";

        mockMvc.perform(get("/internal/v1/users/usr-1/contact")
                        .header("Authorization", "Bearer " + dummyJwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Spoofed identity headers do not bypass authentication")
    void spoofedHeaders_areIgnored() throws Exception {
        mockMvc.perform(get("/internal/v1/users/usr-1/contact")
                        .header("X-User-Id", "usr-admin-1")
                        .header("X-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("notifications-service is FORBIDDEN from calling merchant provisioning endpoints (least privilege)")
    void notificationsService_cannotCallProvisioning() throws Exception {
        InternalCreateUserRequest userReq = new InternalCreateUserRequest(
                "new.merchant@test.com", "Password123!", "New Merchant", "+94779998888", Roles.MERCHANT, "mch-999", false);

        mockMvc.perform(post("/internal/v1/users")
                        .header(INTERNAL_TOKEN_HEADER, NOTIF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/v1/users/usr-1/activate")
                        .header(INTERNAL_TOKEN_HEADER, NOTIF_TOKEN))
                .andExpect(status().isForbidden());

        InternalCreateClientRequest clientReq = new InternalCreateClientRequest(
                "mch-999", "Daraz Client", List.of("parcels:create"));

        mockMvc.perform(post("/internal/v1/clients")
                        .header(INTERNAL_TOKEN_HEADER, NOTIF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("notifications-service can perform contact lookup with valid token")
    void notificationsService_canLookupContact() throws Exception {
        User user = new User("usr-notif-1", "alice@example.com", "hash", "Alice Smith",
                "+94771234567", Roles.CUSTOMER, true, null);
        userRepository.save(user);

        mockMvc.perform(get("/internal/v1/users/usr-notif-1/contact")
                        .header(INTERNAL_TOKEN_HEADER, NOTIF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("usr-notif-1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alice Smith"));
    }

    @Test
    @DisplayName("merchant-service can call user creation and activation with valid token")
    void merchantService_canPerformProvisioning() throws Exception {
        InternalCreateUserRequest userReq = new InternalCreateUserRequest(
                "onboarding@store.com", "Password123!", "Merchant Store", "+94779998888",
                Roles.MERCHANT, "mch-sec-1", false);

        mockMvc.perform(post("/internal/v1/users")
                        .header(INTERNAL_TOKEN_HEADER, MERCHANT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("onboarding@store.com"))
                .andExpect(jsonPath("$.enabled").value(false));
    }
}
