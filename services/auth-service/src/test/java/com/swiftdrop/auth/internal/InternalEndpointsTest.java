package com.swiftdrop.auth.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import com.swiftdrop.auth.client.ClientCredential;
import com.swiftdrop.auth.client.ClientCredentialRepository;
import com.swiftdrop.auth.internal.dto.ClientTokenRequest;
import com.swiftdrop.auth.internal.dto.InternalCreateClientRequest;
import com.swiftdrop.auth.internal.dto.InternalCreateUserRequest;
import com.swiftdrop.auth.user.User;
import com.swiftdrop.auth.user.UserRepository;
import com.swiftdrop.common.Roles;
import com.swiftdrop.common.security.JwtClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InternalEndpointsTest {

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        clientCredentialRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("GET /internal/v1/users/{id}/contact")
    class GetUserContactTests {

        @Test
        @DisplayName("Returns 200 with user contact details when user exists")
        void returnsContactDetailsWhenUserExists() throws Exception {
            User user = new User("usr-12345", "kasun@example.com", "hash", "Kasun Perera",
                    "+94771234567", Roles.CUSTOMER, true, null);
            userRepository.save(user);

            mockMvc.perform(get("/internal/v1/users/{id}/contact", "usr-12345"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId", is("usr-12345")))
                    .andExpect(jsonPath("$.fullName", is("Kasun Perera")))
                    .andExpect(jsonPath("$.email", is("kasun@example.com")))
                    .andExpect(jsonPath("$.phoneNumber", is("+94771234567")))
                    .andExpect(jsonPath("$.role", is(Roles.CUSTOMER)));
        }

        @Test
        @DisplayName("Returns 404 when user does not exist")
        void returns404WhenUserNotFound() throws Exception {
            mockMvc.perform(get("/internal/v1/users/{id}/contact", "non-existent-user"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", notNullValue()))
                    .andExpect(jsonPath("$.message", notNullValue()));
        }
    }

    @Nested
    @DisplayName("POST /internal/v1/users")
    class CreateUserInternalTests {

        @Test
        @DisplayName("Returns 201 and normalizes email and hashes password")
        void createsUserSuccessfully() throws Exception {
            InternalCreateUserRequest request = new InternalCreateUserRequest(
                    "Merchant@Daraz.LK",
                    "P@ssword123!",
                    "Daraz Operations",
                    "+94112345678",
                    Roles.MERCHANT,
                    "mch-5501",
                    false
            );

            MvcResult result = mockMvc.perform(post("/internal/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.email", is("merchant@daraz.lk")))
                    .andExpect(jsonPath("$.fullName", is("Daraz Operations")))
                    .andExpect(jsonPath("$.phoneNumber", is("+94112345678")))
                    .andExpect(jsonPath("$.role", is(Roles.MERCHANT)))
                    .andExpect(jsonPath("$.merchantId", is("mch-5501")))
                    .andExpect(jsonPath("$.enabled", is(false)))
                    .andExpect(jsonPath("$.createdAt", notNullValue()))
                    .andReturn();

            User savedUser = userRepository.findByEmailIgnoreCase("merchant@daraz.lk").orElseThrow();
            assertThat(savedUser.getEmail()).isEqualTo("merchant@daraz.lk");
            assertThat(passwordEncoder.matches("P@ssword123!", savedUser.getPasswordHash())).isTrue();
            assertThat(savedUser.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Returns 409 Conflict when email is already registered")
        void returns409WhenEmailExists() throws Exception {
            User existing = new User("usr-existing", "existing@example.com", "hash", "Existing User",
                    null, Roles.CUSTOMER, true, null);
            userRepository.save(existing);

            InternalCreateUserRequest request = new InternalCreateUserRequest(
                    "EXISTING@example.com",
                    "P@ssword123!",
                    "New Name",
                    null,
                    Roles.CUSTOMER,
                    null,
                    true
            );

            mockMvc.perform(post("/internal/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", notNullValue()));
        }

        @Test
        @DisplayName("Returns 400 Bad Request for unsupported role")
        void returns400ForInvalidRole() throws Exception {
            InternalCreateUserRequest request = new InternalCreateUserRequest(
                    "user@example.com",
                    "P@ssword123!",
                    "Test User",
                    null,
                    "SUPERUSER",
                    null,
                    true
            );

            mockMvc.perform(post("/internal/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 Bad Request for invalid email format")
        void returns400ForInvalidEmail() throws Exception {
            InternalCreateUserRequest request = new InternalCreateUserRequest(
                    "not-an-email",
                    "P@ssword123!",
                    "Test User",
                    null,
                    Roles.CUSTOMER,
                    null,
                    true
            );

            mockMvc.perform(post("/internal/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /internal/v1/users/{id}/activate")
    class ActivateUserInternalTests {

        @Test
        @DisplayName("Returns 200 and sets enabled=true on existing pending user")
        void activatesUserSuccessfully() throws Exception {
            User pending = new User("usr-merchant-pending", "merchant@pending.lk", "hash", "Pending Merchant",
                    "+94112223344", Roles.MERCHANT, false, "mch-9901");
            userRepository.save(pending);

            mockMvc.perform(post("/internal/v1/users/{id}/activate", "usr-merchant-pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is("usr-merchant-pending")))
                    .andExpect(jsonPath("$.enabled", is(true)));

            User updated = userRepository.findById("usr-merchant-pending").orElseThrow();
            assertThat(updated.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Returns 404 when activating non-existent user")
        void returns404WhenActivatingNonExistent() throws Exception {
            mockMvc.perform(post("/internal/v1/users/{id}/activate", "usr-unknown"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /internal/v1/clients and /token")
    class ClientCredentialsTests {

        @Test
        @DisplayName("Creates client credentials for approved merchant and exchanges for valid RS256 token")
        void createsCredentialsAndIssuesToken() throws Exception {
            // Seed merchant user
            User merchantUser = new User("usr-mch", "merchant@store.lk", "hash", "Store Owner",
                    null, Roles.MERCHANT, true, "mch-5501");
            userRepository.save(merchantUser);

            InternalCreateClientRequest createRequest = new InternalCreateClientRequest(
                    "mch-5501",
                    "Daraz Live Integration",
                    List.of("parcels:create", "parcels:read")
            );

            MvcResult createResult = mockMvc.perform(post("/internal/v1/clients")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.clientId", notNullValue()))
                    .andExpect(jsonPath("$.clientSecret", notNullValue()))
                    .andExpect(jsonPath("$.merchantId", is("mch-5501")))
                    .andExpect(jsonPath("$.clientName", is("Daraz Live Integration")))
                    .andExpect(jsonPath("$.scopes[0]", is("parcels:create")))
                    .andExpect(jsonPath("$.scopes[1]", is("parcels:read")))
                    .andReturn();

            String responseBody = createResult.getResponse().getContentAsString();
            String clientId = objectMapper.readTree(responseBody).get("clientId").asText();
            String clientSecret = objectMapper.readTree(responseBody).get("clientSecret").asText();

            // Verify stored in DB with hashed secret
            ClientCredential credential = clientCredentialRepository.findByClientId(clientId).orElseThrow();
            assertThat(credential.getClientSecretHash()).isNotEqualTo(clientSecret);
            assertThat(passwordEncoder.matches(clientSecret, credential.getClientSecretHash())).isTrue();

            // Now exchange for client credentials token
            ClientTokenRequest tokenRequest = new ClientTokenRequest(clientId, clientSecret);
            MvcResult tokenResult = mockMvc.perform(post("/internal/v1/clients/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(tokenRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.tokenType", is("Bearer")))
                    .andExpect(jsonPath("$.expiresIn", is(3600)))
                    .andReturn();

            String tokenResponse = tokenResult.getResponse().getContentAsString();
            String jwt = objectMapper.readTree(tokenResponse).get("accessToken").asText();

            // Verify token claims
            SignedJWT signedJWT = SignedJWT.parse(jwt);
            var claims = signedJWT.getJWTClaimsSet();
            assertThat(claims.getSubject()).isEqualTo(clientId);
            assertThat(claims.getStringClaim(JwtClaims.MERCHANT_ID)).isEqualTo("mch-5501");
            assertThat(claims.getStringClaim(JwtClaims.ROLE)).isEqualTo(Roles.MERCHANT);
            assertThat(claims.getStringClaim(JwtClaims.SCOPE)).isEqualTo("parcels:create parcels:read");
        }

        @Test
        @DisplayName("Returns 404 when creating client for unknown merchant")
        void returns404WhenMerchantNotFound() throws Exception {
            InternalCreateClientRequest request = new InternalCreateClientRequest(
                    "non-existent-merchant",
                    "Store Integration",
                    List.of("parcels:create")
            );

            mockMvc.perform(post("/internal/v1/clients")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", notNullValue()));
        }

        @Test
        @DisplayName("Returns 401 when exchanging token with invalid secret")
        void returns401WithInvalidSecret() throws Exception {
            User merchantUser = new User("usr-mch", "merchant@store.lk", "hash", "Store Owner",
                    null, Roles.MERCHANT, true, "mch-7701");
            userRepository.save(merchantUser);

            ClientCredential credential = new ClientCredential(
                    "sd_live_client_test",
                    passwordEncoder.encode("correct-secret"),
                    "mch-7701",
                    "Test Store",
                    "parcels:create"
            );
            clientCredentialRepository.save(credential);

            ClientTokenRequest request = new ClientTokenRequest("sd_live_client_test", "wrong-secret");
            mockMvc.perform(post("/internal/v1/clients/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Returns 403 when merchant account is disabled")
        void returns403WhenMerchantDisabled() throws Exception {
            User merchantUser = new User("usr-mch-disabled", "merchant@disabled.lk", "hash", "Disabled Merchant",
                    null, Roles.MERCHANT, false, "mch-8801");
            userRepository.save(merchantUser);

            ClientCredential credential = new ClientCredential(
                    "sd_live_client_disabled",
                    passwordEncoder.encode("secret"),
                    "mch-8801",
                    "Disabled Store",
                    "parcels:create"
            );
            clientCredentialRepository.save(credential);

            ClientTokenRequest request = new ClientTokenRequest("sd_live_client_disabled", "secret");
            mockMvc.perform(post("/internal/v1/clients/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }
}
