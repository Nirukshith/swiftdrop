package com.swiftdrop.integration.merchant;

import static org.mockito.ArgumentMatchers.any;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.integration.merchant.AuthProvisioningClient.AuthClientCredentialsResponse;
import com.swiftdrop.integration.merchant.AuthProvisioningClient.AuthUserResponse;
import com.swiftdrop.integration.merchant.AuthProvisioningClient.AuthCreateClientRequest;
import com.swiftdrop.integration.merchant.AuthProvisioningClient.AuthCreateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MerchantOnboardingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired MerchantRepository merchantRepository;
    @MockitoBean AuthProvisioningClient authProvisioningClient;

    @BeforeEach
    void cleanDatabase() {
        merchantRepository.deleteAll();
    }

    @Test
    void publicRegistrationPersistsPendingMerchant() throws Exception {
        mockMvc.perform(post("/api/v1/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"North Star Stores","contactName":"Asha Perera",
                                 "email":"ASHA@example.test","phoneNumber":"+94771234567"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.email").value("asha@example.test"))
                .andExpect(jsonPath("$.message").value("Registration submitted for administrator review."));

        org.assertj.core.api.Assertions.assertThat(merchantRepository.count()).isEqualTo(1);
        org.mockito.Mockito.verifyNoInteractions(authProvisioningClient);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        register("duplicate@example.test");

        mockMvc.perform(post("/api/v1/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("DUPLICATE@example.test")))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListPendingMerchants() throws Exception {
        register("pending@example.test");

        mockMvc.perform(get("/api/v1/admin/merchants").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void nonAdminCannotReadAdminMerchantList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/merchants")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approvalProvisionsUserActivatesAndReturnsSecretOnce() throws Exception {
        Merchant merchant = register("approve@example.test");
        when(authProvisioningClient.createMerchantUser(any(AuthCreateUserRequest.class)))
                .thenReturn(new AuthUserResponse("usr-123", merchant.getEmail(), merchant.getContactName(),
                        merchant.getPhoneNumber(), "MERCHANT", merchant.getId().toString(), false));
        when(authProvisioningClient.activateUser("usr-123"))
                .thenReturn(new AuthUserResponse("usr-123", merchant.getEmail(), merchant.getContactName(),
                        merchant.getPhoneNumber(), "MERCHANT", merchant.getId().toString(), true));
        when(authProvisioningClient.createClient(any(AuthCreateClientRequest.class)))
                .thenReturn(new AuthClientCredentialsResponse("sd_live_123", "one-time-secret",
                        merchant.getId().toString(), merchant.getBusinessName(), java.util.List.of("parcels:create")));

        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.merchant.status").value("APPROVED"))
                .andExpect(jsonPath("$.clientId").value("sd_live_123"))
                .andExpect(jsonPath("$.clientSecret").value("one-time-secret"));

        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/merchants").param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.content[0].clientSecret").doesNotExist());

        verify(authProvisioningClient).createMerchantUser(any(AuthCreateUserRequest.class));
        verify(authProvisioningClient).activateUser("usr-123");
        verify(authProvisioningClient).createClient(any(AuthCreateClientRequest.class));
        org.assertj.core.api.Assertions.assertThat(merchantRepository.findById(merchant.getId()).orElseThrow().getStatus())
                .isEqualTo(MerchantStatus.APPROVED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void failedProvisioningRemainsPendingAndRetriesWithoutRecreatingUser() throws Exception {
        Merchant merchant = register("retry@example.test");
        when(authProvisioningClient.createMerchantUser(any(AuthCreateUserRequest.class)))
                .thenReturn(new AuthUserResponse("usr-retry", merchant.getEmail(), merchant.getContactName(),
                        merchant.getPhoneNumber(), "MERCHANT", merchant.getId().toString(), false));
        when(authProvisioningClient.activateUser("usr-retry"))
                .thenReturn(new AuthUserResponse("usr-retry", merchant.getEmail(), merchant.getContactName(),
                        merchant.getPhoneNumber(), "MERCHANT", merchant.getId().toString(), true));
        when(authProvisioningClient.createClient(any(AuthCreateClientRequest.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("Auth unavailable"))
                .thenReturn(new AuthClientCredentialsResponse("retry-client", "retry-secret",
                        merchant.getId().toString(), merchant.getBusinessName(), java.util.List.of()));

        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
        Merchant pending = merchantRepository.findById(merchant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(pending.getStatus()).isEqualTo(MerchantStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(pending.getAuthUserId()).isEqualTo("usr-retry");

        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant.status").value("APPROVED"))
                .andExpect(jsonPath("$.clientSecret").value("retry-secret"));
        verify(authProvisioningClient, times(1)).createMerchantUser(any(AuthCreateUserRequest.class));
        verify(authProvisioningClient, times(2)).createClient(any(AuthCreateClientRequest.class));
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void dispatcherCannotApproveOrProvisionCredentials() throws Exception {
        Merchant merchant = register("forbidden@example.test");
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isForbidden());
        verify(authProvisioningClient, never()).createMerchantUser(any(AuthCreateUserRequest.class));
        verify(authProvisioningClient, never()).createClient(any(AuthCreateClientRequest.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectPersistsReasonAndRejectsDuplicateTransition() throws Exception {
        Merchant merchant = register("reject@example.test");
        String payload = "{\"reason\":\"Business details could not be verified\"}";

        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/reject", merchant.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Business details could not be verified"));

        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/reject", merchant.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "blank-id", "wrong-role", "wrong-merchant", "wrong-email", "active"})
    @WithMockUser(roles = "ADMIN")
    void invalidCreatedUserCannotBeLinkedOrActivated(String variant) throws Exception {
        Merchant merchant = register("invalid-user@example.test");
        AuthUserResponse response = "missing".equals(variant) ? null : new AuthUserResponse(
                "blank-id".equals(variant) ? " " : "usr-validation",
                "wrong-email".equals(variant) ? "other@example.test" : merchant.getEmail(),
                merchant.getContactName(), merchant.getPhoneNumber(),
                "wrong-role".equals(variant) ? "ADMIN" : "MERCHANT",
                "wrong-merchant".equals(variant) ? java.util.UUID.randomUUID().toString() : merchant.getId().toString(),
                "active".equals(variant));
        when(authProvisioningClient.createMerchantUser(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
        Merchant saved = merchantRepository.findById(merchant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(MerchantStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(saved.getAuthUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(saved.getClientId()).isNull();
        verify(authProvisioningClient, never()).activateUser(any());
        verify(authProvisioningClient, never()).createClient(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "disabled", "wrong-id", "wrong-role", "wrong-merchant", "wrong-email"})
    @WithMockUser(roles = "ADMIN")
    void invalidActivationCannotIssueCredentialsOrApprove(String variant) throws Exception {
        Merchant merchant = register("invalid-activation@example.test");
        stubCreatedUser(merchant);
        AuthUserResponse response = "missing".equals(variant) ? null : new AuthUserResponse(
                "wrong-id".equals(variant) ? "usr-other" : "usr-validation",
                "wrong-email".equals(variant) ? "other@example.test" : merchant.getEmail(),
                merchant.getContactName(), merchant.getPhoneNumber(),
                "wrong-role".equals(variant) ? "CUSTOMER" : "MERCHANT",
                "wrong-merchant".equals(variant) ? java.util.UUID.randomUUID().toString() : merchant.getId().toString(),
                !"disabled".equals(variant));
        when(authProvisioningClient.activateUser("usr-validation")).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isBadGateway());
        Merchant saved = merchantRepository.findById(merchant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(MerchantStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(saved.getAuthUserId()).isEqualTo("usr-validation");
        org.assertj.core.api.Assertions.assertThat(saved.getClientId()).isNull();
        verify(authProvisioningClient, never()).createClient(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null-id", "blank-id", "null-secret", "blank-secret", "wrong-merchant"})
    @WithMockUser(roles = "ADMIN")
    void invalidCredentialsRemainPendingAndAreNeverHandedToAdmin(String variant) throws Exception {
        Merchant merchant = register("invalid-client@example.test");
        stubCreatedUser(merchant);
        when(authProvisioningClient.activateUser("usr-validation"))
                .thenReturn(authUser(merchant, true));
        AuthClientCredentialsResponse response = "missing".equals(variant) ? null : new AuthClientCredentialsResponse(
                "null-id".equals(variant) ? null : "blank-id".equals(variant) ? " " : "client-validation",
                "null-secret".equals(variant) ? null : "blank-secret".equals(variant) ? " " : "test-only-secret",
                "wrong-merchant".equals(variant) ? java.util.UUID.randomUUID().toString() : merchant.getId().toString(),
                merchant.getBusinessName(), java.util.List.of("parcels:create"));
        when(authProvisioningClient.createClient(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.clientId").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist())
                .andExpect(content().string(not(containsString("test-only-secret"))));
        Merchant saved = merchantRepository.findById(merchant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(MerchantStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(saved.getClientId()).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void failedActivationCanRetryUsingTheSameProvisionedUser() throws Exception {
        Merchant merchant = register("activation-retry@example.test");
        stubCreatedUser(merchant);
        when(authProvisioningClient.activateUser("usr-validation"))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Auth unavailable"))
                .thenReturn(authUser(merchant, true));
        when(authProvisioningClient.createClient(any())).thenReturn(new AuthClientCredentialsResponse(
                "client-validation", "test-only-secret", merchant.getId().toString(), merchant.getBusinessName(),
                java.util.List.of("parcels:create")));
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isBadGateway());
        verify(authProvisioningClient, never()).createClient(any());
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.merchant.status").value("APPROVED"));
        verify(authProvisioningClient, times(1)).createMerchantUser(any());
        verify(authProvisioningClient, times(2)).activateUser("usr-validation");
        var order = org.mockito.Mockito.inOrder(authProvisioningClient);
        order.verify(authProvisioningClient).createMerchantUser(new AuthCreateUserRequest(merchant.getEmail(), null,
                merchant.getContactName(), merchant.getPhoneNumber(), "MERCHANT", merchant.getId().toString(), false));
        order.verify(authProvisioningClient, times(2)).activateUser("usr-validation");
        order.verify(authProvisioningClient).createClient(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectionDoesNotCreateActivateOrIssueCredentials() throws Exception {
        Merchant merchant = register("reject-no-auth@example.test");
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/reject", merchant.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Business not verified\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isConflict());
        org.mockito.Mockito.verifyNoInteractions(authProvisioningClient);
    }

    private void stubCreatedUser(Merchant merchant) {
        when(authProvisioningClient.createMerchantUser(any())).thenReturn(authUser(merchant, false));
    }

    private AuthUserResponse authUser(Merchant merchant, boolean enabled) {
        return new AuthUserResponse("usr-validation", merchant.getEmail(), merchant.getContactName(),
                merchant.getPhoneNumber(), "MERCHANT", merchant.getId().toString(), enabled);
    }

    private Merchant register(String email) throws Exception {
        return merchantRepository.saveAndFlush(new Merchant(java.util.UUID.randomUUID(), "Store", "Contact",
                email.toLowerCase(), "+94771234567", null, java.time.Instant.now()));
    }

    private String registrationJson(String email) {
        return "{\"businessName\":\"North Star Stores\",\"contactName\":\"Asha Perera\","
                + "\"email\":\"" + email + "\",\"phoneNumber\":\"+94771234567\"}";
    }
}
