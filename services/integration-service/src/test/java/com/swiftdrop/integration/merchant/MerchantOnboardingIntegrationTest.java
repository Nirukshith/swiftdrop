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

import com.swiftdrop.integration.merchant.AuthProvisioningClient.ProvisioningResponse;
import com.swiftdrop.integration.merchant.AuthProvisioningClient.AuthUserResponse;


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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
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
        stubProvision(merchant, "one-time-secret");
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.merchant.status").value("APPROVED"))
                .andExpect(jsonPath("$.clientId").value("client-validation"))
                .andExpect(jsonPath("$.clientSecret").value("one-time-secret"));
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve", merchant.getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.clientSecret").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/merchants").param("status","APPROVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].clientSecret").doesNotExist());
        verify(authProvisioningClient,times(1)).provision(any(),any());
        org.assertj.core.api.Assertions.assertThat(merchantRepository.findById(merchant.getId()).orElseThrow().getStatus()).isEqualTo(MerchantStatus.APPROVED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void failedProvisioningRemainsPendingAndRetriesWithoutRecreatingUser() throws Exception {
        Merchant merchant=register("retry@example.test");
        org.mockito.Mockito.doThrow(new org.springframework.web.client.ResourceAccessException("Response lost"))
                .doAnswer(call -> response(call.getArgument(0),merchant,authUser(merchant,true),"test-secret",false))
                .when(authProvisioningClient).provision(any(),any());
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve",merchant.getId()))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.clientSecret").doesNotExist());
        var pending=merchantRepository.findById(merchant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(pending.getStatus()).isEqualTo(MerchantStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(pending.getAuthUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(pending.getProvisioningOperationId()).isNotNull();
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve",merchant.getId())).andExpect(status().isOk());
        var ids=org.mockito.ArgumentCaptor.forClass(java.util.UUID.class);
        verify(authProvisioningClient,times(2)).provision(ids.capture(),any());
        org.assertj.core.api.Assertions.assertThat(ids.getAllValues()).containsOnly(pending.getProvisioningOperationId());
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void dispatcherCannotApproveOrProvisionCredentials() throws Exception {
        Merchant merchant=register("forbidden@example.test");
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve",merchant.getId())).andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(authProvisioningClient);
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
    @ValueSource(strings = {"missing", "blank-id", "wrong-role", "wrong-merchant", "wrong-email", "disabled"})
    @WithMockUser(roles = "ADMIN")
    void invalidCreatedUserCannotBeLinkedOrActivated(String variant) throws Exception {
        Merchant merchant=register("invalid-user@example.test");
        AuthUserResponse user="missing".equals(variant)?null:new AuthUserResponse(
                "blank-id".equals(variant)?" ":"usr-validation",
                "wrong-email".equals(variant)?"other@example.test":merchant.getEmail(),merchant.getContactName(),merchant.getPhoneNumber(),
                "wrong-role".equals(variant)?"ADMIN":"MERCHANT",
                "wrong-merchant".equals(variant)?java.util.UUID.randomUUID().toString():merchant.getId().toString(),!"disabled".equals(variant));
        when(authProvisioningClient.provision(any(),any())).thenAnswer(call -> response(call.getArgument(0),merchant,user,"test-secret",false));
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve",merchant.getId()))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.clientSecret").doesNotExist());
        var saved=merchantRepository.findById(merchant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(MerchantStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(saved.getAuthUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(saved.getClientId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "disabled", "wrong-id", "wrong-role", "wrong-merchant", "wrong-email"})
    @WithMockUser(roles = "ADMIN")
    void invalidActivationCannotIssueCredentialsOrApprove(String variant) throws Exception {
        Merchant merchant=register("invalid-activation@example.test");
        stubCreatedUser(merchant);
        AuthUserResponse user="missing".equals(variant)?null:new AuthUserResponse(
                "wrong-id".equals(variant)?"usr-other":"usr-validation",
                "wrong-email".equals(variant)?"other@example.test":merchant.getEmail(),merchant.getContactName(),merchant.getPhoneNumber(),
                "wrong-role".equals(variant)?"CUSTOMER":"MERCHANT",
                "wrong-merchant".equals(variant)?java.util.UUID.randomUUID().toString():merchant.getId().toString(),!"disabled".equals(variant));
        when(authProvisioningClient.provision(any(),any())).thenAnswer(call -> response(call.getArgument(0),merchant,user,"test-secret",false));
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve",merchant.getId())).andExpect(status().isBadGateway());
        var saved=merchantRepository.findById(merchant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(MerchantStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(saved.getAuthUserId()).isEqualTo("usr-validation");
        org.assertj.core.api.Assertions.assertThat(saved.getClientId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null-id", "blank-id", "null-secret", "blank-secret", "wrong-merchant"})
    @WithMockUser(roles = "ADMIN")
    void invalidCredentialsRemainPendingAndAreNeverHandedToAdmin(String variant) throws Exception {
        Merchant merchant=register("invalid-client@example.test");
        stubCreatedUser(merchant);
        when(authProvisioningClient.provision(any(),any())).thenAnswer(call -> "missing".equals(variant)?null:new ProvisioningResponse(
                call.getArgument(0),"wrong-merchant".equals(variant)?java.util.UUID.randomUUID():merchant.getId(),authUser(merchant,true),
                "null-id".equals(variant)?null:"blank-id".equals(variant)?" ":"client-validation",
                "null-secret".equals(variant)?null:"blank-secret".equals(variant)?" ":"test-only-secret",false));
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve",merchant.getId()))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.clientId").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist()).andExpect(content().string(not(containsString("test-only-secret"))));
        var saved=merchantRepository.findById(merchant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(MerchantStatus.PENDING);
        org.assertj.core.api.Assertions.assertThat(saved.getClientId()).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void failedActivationCanRetryUsingTheSameProvisionedUser() throws Exception {
        Merchant merchant=register("activation-retry@example.test");
        stubCreatedUser(merchant);
        org.mockito.Mockito.doThrow(new org.springframework.web.client.ResourceAccessException("Unavailable"))
                .doAnswer(call -> response(call.getArgument(0),merchant,authUser(merchant,true),"test-secret",false))
                .when(authProvisioningClient).provision(any(),any());
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve",merchant.getId())).andExpect(status().isBadGateway());
        mockMvc.perform(post("/api/v1/admin/merchants/{merchantId}/approve",merchant.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.merchant.status").value("APPROVED"));
        var requests=org.mockito.ArgumentCaptor.forClass(AuthProvisioningClient.ProvisioningRequest.class);
        var ids=org.mockito.ArgumentCaptor.forClass(java.util.UUID.class);
        verify(authProvisioningClient,times(2)).provision(ids.capture(),requests.capture());
        org.assertj.core.api.Assertions.assertThat(ids.getAllValues().getFirst()).isEqualTo(ids.getAllValues().getLast());
        org.assertj.core.api.Assertions.assertThat(requests.getAllValues()).allSatisfy(r -> {
            org.assertj.core.api.Assertions.assertThat(r.expectedUserId()).isEqualTo("usr-validation");
            org.assertj.core.api.Assertions.assertThat(r.merchantId()).isEqualTo(merchant.getId());
            org.assertj.core.api.Assertions.assertThat(r.actorId()).isEqualTo("user");
        });
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

    @Test @WithMockUser(roles="ADMIN")
    void localCommitFailureRetainsOperationAndRequiresControlledSecretRecovery() throws Exception {
        Merchant merchant=register("local-failure@example.test"); stubProvision(merchant,"lost-after-auth-commit");
        jdbc.execute("CREATE FUNCTION reject_test_approval() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='APPROVED' THEN RAISE EXCEPTION 'simulated local persistence failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_approval_failure BEFORE UPDATE ON merchants FOR EACH ROW EXECUTE FUNCTION reject_test_approval()");
        try {
            mockMvc.perform(post("/api/v1/admin/merchants/{id}/approve",merchant.getId())).andExpect(status().isBadGateway());
            var saved=merchantRepository.findById(merchant.getId()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(MerchantStatus.PENDING);
            org.assertj.core.api.Assertions.assertThat(saved.getProvisioningOperationId()).isNotNull();
        } finally {
            jdbc.execute("DROP TRIGGER test_approval_failure ON merchants"); jdbc.execute("DROP FUNCTION reject_test_approval()");
        }
        stubProvision(merchant,null);
        mockMvc.perform(post("/api/v1/admin/merchants/{id}/approve",merchant.getId())).andExpect(status().isConflict());
        when(authProvisioningClient.replaceCredential(any(),any())).thenAnswer(call -> response(call.getArgument(0),merchant,authUser(merchant,true),"recovered-secret",false));
        mockMvc.perform(post("/api/v1/admin/merchants/{id}/recover-credentials",merchant.getId()).header("Idempotency-Key",java.util.UUID.randomUUID())
                .contentType("application/json").content("{\"confirmReplacement\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clientSecret").value("recovered-secret"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from merchant_provisioning_audit where merchant_id=? and outcome='OUTCOME_UNAVAILABLE'",Integer.class,merchant.getId())).isEqualTo(1);
    }

    private void stubProvision(Merchant merchant,String secret) {
        when(authProvisioningClient.provision(any(),any())).thenAnswer(call -> response(call.getArgument(0),merchant,authUser(merchant,true),secret,secret==null));
    }
    private ProvisioningResponse response(java.util.UUID operation,Merchant merchant,AuthUserResponse user,String secret,boolean recovery) {
        return new ProvisioningResponse(operation,merchant.getId(),user,"client-validation",secret,recovery);
    }

    @Test @WithMockUser(roles="ADMIN")
    void lostSecretRequiresExplicitRecoveryAndRejectsDuplicateReplacementWithoutAnotherSecret() throws Exception {
        Merchant merchant=register("lost@example.test"); stubProvision(merchant,null);
        mockMvc.perform(post("/api/v1/admin/merchants/{id}/approve",merchant.getId())).andExpect(status().isConflict());
        var pending=merchantRepository.findById(merchant.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(pending.getStatus()).isEqualTo(MerchantStatus.PENDING);
        verify(authProvisioningClient,never()).replaceCredential(any(),any());
        when(authProvisioningClient.replaceCredential(any(),any())).thenAnswer(call -> response(call.getArgument(0),merchant,authUser(merchant,true),"replacement-secret",false));
        var key=java.util.UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/admin/merchants/{id}/recover-credentials",merchant.getId()).header("Idempotency-Key",key)
                .contentType("application/json").content("{\"confirmReplacement\":true}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.merchant.status").value("APPROVED"))
                .andExpect(jsonPath("$.clientId").value("client-validation")).andExpect(jsonPath("$.clientSecret").value("replacement-secret"));
        org.mockito.Mockito.doAnswer(call -> response(call.getArgument(0),merchant,authUser(merchant,true),null,true)).when(authProvisioningClient).replaceCredential(any(),any());
        mockMvc.perform(post("/api/v1/admin/merchants/{id}/recover-credentials",merchant.getId()).header("Idempotency-Key",key)
                .contentType("application/json").content("{\"confirmReplacement\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.clientSecret").doesNotExist());
    }
    @Test @WithMockUser(roles="ADMIN")
    void recoveryRequiresConfirmationAndProvisioningBlocksUnsafeRejection() throws Exception {
        Merchant merchant=register("confirmation@example.test");
        mockMvc.perform(post("/api/v1/admin/merchants/{id}/recover-credentials",merchant.getId()).header("Idempotency-Key",java.util.UUID.randomUUID())
                .contentType("application/json").content("{\"confirmReplacement\":false}")).andExpect(status().isBadRequest());
        stubProvision(merchant,null);
        mockMvc.perform(post("/api/v1/admin/merchants/{id}/approve",merchant.getId())).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/merchants/{id}/reject",merchant.getId()).contentType("application/json").content("{\"reason\":\"Reject\"}"))
                .andExpect(status().isConflict());
    }
    @Test @WithMockUser(roles="CUSTOMER")
    void nonAdminCannotRecoverCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/admin/merchants/{id}/recover-credentials",java.util.UUID.randomUUID())
                .header("Idempotency-Key",java.util.UUID.randomUUID()).contentType("application/json").content("{\"confirmReplacement\":true}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(authProvisioningClient);
    }

    private void stubCreatedUser(Merchant merchant) {
        merchant.setAuthUserId("usr-validation");
        merchantRepository.saveAndFlush(merchant);
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
