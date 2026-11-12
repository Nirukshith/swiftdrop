package com.swiftdrop.auth.internal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties={"swiftdrop.security.internal.tokens.merchant-service=recovery-test-merchant", "swiftdrop.security.internal.tokens.notifications-service=recovery-test-notifications"})
@AutoConfigureMockMvc @Testcontainers
class MerchantProvisioningIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    UUID merchant, operation;

    @BeforeEach void clean() {
        jdbc.update("delete from clients"); jdbc.update("delete from users");
        merchant=UUID.randomUUID(); operation=UUID.randomUUID();
    }
    String path() { return "/internal/v1/merchant-provisioning/"+operation; }
    String body() { return """
        {"merchantId":"%s","email":"shop@example.test","fullName":"Shop Owner",
         "phoneNumber":"+94771234567","actorId":"verified-admin"}
        """.formatted(merchant); }
    JsonNode provision() throws Exception {
        return mapper.readTree(mvc.perform(put(path()).header("X-Internal-Service-Token","recovery-test-merchant")
                .contentType("application/json").content(body())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString());
    }
    JsonNode replace(UUID key) throws Exception {
        return mapper.readTree(mvc.perform(post(path()+"/credentials").header("X-Internal-Service-Token","recovery-test-merchant")
                .contentType("application/json").content("{\"replacementId\":\""+key+"\",\"actorId\":\"verified-admin\",\"confirmReplacement\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void atomicProvisioningReturnsOneSecretAndOnlyHashesAreStored() throws Exception {
        var first=provision();
        assertThat(first.path("credentialRecoveryRequired").asBoolean()).isFalse();
        assertThat(first.path("user").path("enabled").asBoolean()).isTrue();
        String hash=jdbc.queryForObject("select client_secret_hash from clients",String.class);
        assertThat(passwords.matches(first.path("clientSecret").asText(),hash)).isTrue();
        assertThat(hash).doesNotContain(first.path("clientSecret").asText());
        var again=provision();
        assertThat(again.path("clientId")).isEqualTo(first.path("clientId"));
        assertThat(again.path("clientSecret").isNull()).isTrue();
        assertThat(again.path("credentialRecoveryRequired").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from users",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from clients",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from merchant_provisioning_events",Integer.class)).isEqualTo(1);
    }
    @Test void statusAndReplayAfterAnIgnoredResponseNeverRevealTheSecret() throws Exception {
        provision();
        mvc.perform(get(path()).header("X-Internal-Service-Token","recovery-test-merchant"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clientSecret").isEmpty())
                .andExpect(jsonPath("$.credentialRecoveryRequired").value(true));
        assertThat(provision().path("clientSecret").isNull()).isTrue();
    }

    @Test void legacyEndpointsCannotBypassManagedProvisioning() throws Exception {
        provision();
        mvc.perform(post("/internal/v1/clients").header("X-Internal-Service-Token","recovery-test-merchant")
                .contentType("application/json").content("{\"merchantId\":\""+merchant+"\",\"clientName\":\"Duplicate\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/internal/v1/users").header("X-Internal-Service-Token","recovery-test-merchant")
                .contentType("application/json").content("{\"merchantId\":\""+merchant+"\",\"email\":\"second@example.test\",\"fullName\":\"Other\",\"role\":\"MERCHANT\",\"enabled\":true}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from clients",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from users",Integer.class)).isEqualTo(1);
    }

    @Test void concurrentReplacementRotatesOnlyOnceForTheSameConfirmation() throws Exception {
        provision(); UUID key=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(() -> replace(key)); var two=pool.submit(() -> replace(key));
            var a=one.get(); var b=two.get();
            assertThat((a.path("clientSecret").isNull()?0:1)+(b.path("clientSecret").isNull()?0:1)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from merchant_credential_replacements",Integer.class)).isEqualTo(1);
        }
    }
    @Test void explicitReplacementKeepsClientAndRevokesOldSecretWithoutReplayingTheNewSecret() throws Exception {
        var first=provision(); UUID key=UUID.randomUUID(); var changed=replace(key);
        assertThat(changed.path("clientId")).isEqualTo(first.path("clientId"));
        assertThat(changed.path("clientSecret").asText()).isNotEqualTo(first.path("clientSecret").asText());
        String hash=jdbc.queryForObject("select client_secret_hash from clients",String.class);
        assertThat(passwords.matches(first.path("clientSecret").asText(),hash)).isFalse();
        assertThat(passwords.matches(changed.path("clientSecret").asText(),hash)).isTrue();
        assertThat(replace(key).path("clientSecret").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select client_secret_hash from clients",String.class)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("select count(*) from clients",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from merchant_credential_replacements",Integer.class)).isEqualTo(1);
        var tokenBody="{\"grant_type\":\"client_credentials\",\"client_id\":\""+first.path("clientId").asText()+"\",\"client_secret\":\"%s\"}";
        mvc.perform(post("/api/v1/auth/token").contentType("application/json").content(tokenBody.formatted(first.path("clientSecret").asText()))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/token").contentType("application/json").content(tokenBody.formatted(changed.path("clientSecret").asText()))).andExpect(status().isOk());
    }
    @Test void lostReplacementCanBeRecoveredOnlyByAnotherExplicitReplacement() throws Exception {
        provision(); UUID key=UUID.randomUUID(); replace(key);
        assertThat(replace(key).path("clientSecret").isNull()).isTrue();
        assertThat(replace(UUID.randomUUID()).path("clientSecret").asText()).startsWith("sd_sec_");
        assertThat(jdbc.queryForObject("select count(*) from clients",Integer.class)).isEqualTo(1);
    }
    @Test void changedPayloadOperationOrMerchantCannotBeAdopted() throws Exception {
        provision();
        mvc.perform(put(path()).header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json").content(body().replace("Shop Owner","Changed"))).andExpect(status().isConflict());
        mvc.perform(put("/internal/v1/merchant-provisioning/"+UUID.randomUUID()).header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json").content(body())).andExpect(status().isConflict());
        mvc.perform(put(path()).header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json").content(body().replace(merchant.toString(),UUID.randomUUID().toString()))).andExpect(status().isConflict());
    }
    @Test void anonymousNotificationAndSpoofedIdentityCallersCannotProvisionOrRotate() throws Exception {
        mvc.perform(put(path()).contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        mvc.perform(put(path()).header("X-Internal-Service-Token","recovery-test-notifications").contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(put(path()).header("X-Role","ADMIN").contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        provision();
        mvc.perform(post(path()+"/credentials").header("X-Internal-Service-Token","recovery-test-notifications").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
    @Test void missingConfirmationAndMissingOperationHaveNoMutation() throws Exception {
        mvc.perform(get(path()).header("X-Internal-Service-Token","recovery-test-merchant")).andExpect(status().isNotFound());
        provision(); String hash=jdbc.queryForObject("select client_secret_hash from clients",String.class);
        mvc.perform(post(path()+"/credentials").header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json")
                .content("{\"replacementId\":\""+UUID.randomUUID()+"\",\"actorId\":\"admin\",\"confirmReplacement\":false}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select client_secret_hash from clients",String.class)).isEqualTo(hash);
    }
    @Test void foreignEmailIsNotAdoptedAndUnsafeLegacyClientRollsBackActivation() throws Exception {
        jdbc.update("insert into users(id,email,full_name,role,enabled,created_at,updated_at) values ('foreign','shop@example.test','Other','CUSTOMER',true,current_timestamp,current_timestamp)");
        mvc.perform(put(path()).header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json").content(body())).andExpect(status().isConflict());
        jdbc.update("delete from users");
        jdbc.update("insert into users(id,email,full_name,role,enabled,merchant_id,created_at,updated_at) values ('legacy','shop@example.test','Shop Owner','MERCHANT',false,?,current_timestamp,current_timestamp)",merchant.toString());
        for(String id:new String[]{"client-one","client-two"}) jdbc.update("insert into clients(client_id,client_secret_hash,merchant_id,client_name,scopes,status,created_at,updated_at) values (?,'hash',?,'Legacy','parcels:create','ACTIVE',current_timestamp,current_timestamp)",id,merchant.toString());
        mvc.perform(put(path()).header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json").content(body())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select enabled from users where id='legacy'",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from merchant_provisioning",Integer.class)).isZero();
    }
    @Test void legacyMatchingAssociationReconcilesWithoutMintingAnotherClient() throws Exception {
        var first=provision();
        jdbc.update("delete from merchant_provisioning");
        var reconciled=provision();
        assertThat(reconciled.path("clientId")).isEqualTo(first.path("clientId"));
        assertThat(reconciled.path("clientSecret").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from users",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentDuplicateProvisioningReturnsOnlyOneRawSecret() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(this::provision); var two=pool.submit(this::provision);
            var a=one.get(); var b=two.get();
            assertThat(a.path("clientId")).isEqualTo(b.path("clientId"));
            assertThat((a.path("clientSecret").isNull()?0:1)+(b.path("clientSecret").isNull()?0:1)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from clients",Integer.class)).isEqualTo(1);
        }
    }

    @Test void concurrentCrossMerchantOperationCollisionIsAConflictWithoutOrphanAccounts() throws Exception {
        String first=body();
        String second=body().replace(merchant.toString(),UUID.randomUUID().toString()).replace("shop@example.test","other@example.test");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(() -> mvc.perform(put(path()).header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json").content(first)).andReturn().getResponse().getStatus());
            var two=pool.submit(() -> mvc.perform(put(path()).header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json").content(second)).andReturn().getResponse().getStatus());
            assertThat(java.util.List.of(one.get(),two.get())).containsExactlyInAnyOrder(200,409);
            assertThat(jdbc.queryForObject("select count(*) from users",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from clients",Integer.class)).isEqualTo(1);
        }
    }
    @Test void invalidExpectedUserAndCrossOperationReplacementKeysConflict() throws Exception {
        provision();
        mvc.perform(put(path()).header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json")
                .content(body().replace("\"actorId\"", "\"expectedUserId\":\"foreign-user\",\"actorId\""))).andExpect(status().isConflict());
        UUID key=UUID.randomUUID(); replace(key);
        merchant=UUID.randomUUID(); operation=UUID.randomUUID();
        String another=body().replace("shop@example.test","second@example.test");
        mvc.perform(put(path()).header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json").content(another)).andExpect(status().isOk());
        mvc.perform(post(path()+"/credentials").header("X-Internal-Service-Token","recovery-test-merchant").contentType("application/json")
                .content("{\"replacementId\":\""+key+"\",\"actorId\":\"admin\",\"confirmReplacement\":true}"))
                .andExpect(status().isConflict());
    }
}
