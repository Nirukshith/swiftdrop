package com.swiftdrop.integration.merchant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
                .andExpect(jsonPath("$.merchant.status").value("APPROVED"))
                .andExpect(jsonPath("$.clientId").value("sd_live_123"))
                .andExpect(jsonPath("$.clientSecret").value("one-time-secret"));

        verify(authProvisioningClient).createMerchantUser(any(AuthCreateUserRequest.class));
        verify(authProvisioningClient).activateUser("usr-123");
        verify(authProvisioningClient).createClient(any(AuthCreateClientRequest.class));
        org.assertj.core.api.Assertions.assertThat(merchantRepository.findById(merchant.getId()).orElseThrow().getStatus())
                .isEqualTo(MerchantStatus.APPROVED);
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

    private Merchant register(String email) throws Exception {
        return merchantRepository.saveAndFlush(new Merchant(java.util.UUID.randomUUID(), "Store", "Contact",
                email.toLowerCase(), "+94771234567", null, java.time.Instant.now()));
    }

    private String registrationJson(String email) {
        return "{\"businessName\":\"North Star Stores\",\"contactName\":\"Asha Perera\","
                + "\"email\":\"" + email + "\",\"phoneNumber\":\"+94771234567\"}";
    }
}
