package com.swiftdrop.auth.internal.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.client.ClientCredential;
import com.swiftdrop.auth.client.ClientCredentialRepository;
import com.swiftdrop.auth.client.ClientTokenService;
import com.swiftdrop.auth.internal.InternalClientController;
import com.swiftdrop.auth.internal.InternalUserController;
import com.swiftdrop.auth.internal.dto.InternalCreateClientRequest;
import com.swiftdrop.auth.internal.dto.InternalCreateUserRequest;
import com.swiftdrop.auth.security.SecurityConfig;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import com.swiftdrop.common.Roles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {InternalUserController.class, InternalClientController.class})
@Import({SecurityConfig.class, InternalServiceSecurityFilter.class})
class InternalServiceSecurityTest {

    private static final String NOTIF_TOKEN = "swiftdrop-internal-notif-secret-2026";
    private static final String MERCHANT_TOKEN = "swiftdrop-internal-merchant-secret-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ClientCredentialRepository clientCredentialRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private ClientTokenService clientTokenService;

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
                        .header("X-Internal-Service-Token", "invalid-token-123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Ordinary user JWT or merchant token cannot access internal endpoints")
    void userJwt_cannotAccessInternalEndpoints() throws Exception {
        String dummyJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.dummyPayload.dummySig";

        mockMvc.perform(get("/internal/v1/users/usr-1/contact")
                        .header("Authorization", "Bearer " + dummyJwt))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/v1/users")
                        .header("Authorization", "Bearer " + dummyJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
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
    @DisplayName("notifications-service can perform contact lookup")
    void notificationsService_canLookupContact() throws Exception {
        User user = new User("usr-1", "alice@example.com", "hash", "Alice Smith", "+94771234567", Roles.CUSTOMER, true, null);
        when(userRepository.findById("usr-1")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/internal/v1/users/usr-1/contact")
                        .header("X-Internal-Service-Token", NOTIF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("usr-1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alice Smith"));
    }

    @Test
    @DisplayName("notifications-service is FORBIDDEN from calling merchant provisioning endpoints (least privilege)")
    void notificationsService_cannotCallProvisioning() throws Exception {
        InternalCreateUserRequest userReq = new InternalCreateUserRequest(
                "new.merchant@test.com", "Password123!", "New Merchant", "+94779998888", Roles.MERCHANT, "mch-999", false);

        mockMvc.perform(post("/internal/v1/users")
                        .header("X-Internal-Service-Token", NOTIF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/v1/users/usr-1/activate")
                        .header("X-Internal-Service-Token", NOTIF_TOKEN))
                .andExpect(status().isForbidden());

        InternalCreateClientRequest clientReq = new InternalCreateClientRequest(
                "mch-999", "Daraz Client", List.of("parcels:create"));

        mockMvc.perform(post("/internal/v1/clients")
                        .header("X-Internal-Service-Token", NOTIF_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("merchant-service can call user creation, activation, and client provisioning")
    void merchantService_canPerformProvisioning() throws Exception {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User pendingUser = new User("usr-1", "mch@store.com", "hash", "Mch", "+94771234567", Roles.MERCHANT, false, "mch-1");
        when(userRepository.findById("usr-1")).thenReturn(Optional.of(pendingUser));

        when(userRepository.existsByMerchantId("mch-1")).thenReturn(true);
        when(clientCredentialRepository.save(any(ClientCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_secret");

        InternalCreateUserRequest userReq = new InternalCreateUserRequest(
                "onboarding@store.com", "Password123!", "Merchant Store", "+94779998888", Roles.MERCHANT, "mch-1", false);

        // 1. Create user
        mockMvc.perform(post("/internal/v1/users")
                        .header("X-Internal-Service-Token", MERCHANT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("onboarding@store.com"))
                .andExpect(jsonPath("$.enabled").value(false));

        // 2. Activate user
        mockMvc.perform(post("/internal/v1/users/usr-1/activate")
                        .header("X-Internal-Service-Token", MERCHANT_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // 3. Create client credentials
        InternalCreateClientRequest clientReq = new InternalCreateClientRequest(
                "mch-1", "Merchant Production Store", List.of("parcels:create", "parcels:read"));

        mockMvc.perform(post("/internal/v1/clients")
                        .header("X-Internal-Service-Token", MERCHANT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").isNotEmpty())
                .andExpect(jsonPath("$.clientSecret").isNotEmpty())
                .andExpect(jsonPath("$.merchantId").value("mch-1"));
    }
}
