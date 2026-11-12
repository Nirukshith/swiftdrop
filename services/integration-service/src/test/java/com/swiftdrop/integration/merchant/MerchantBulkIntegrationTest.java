package com.swiftdrop.integration.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:9/jwks",
        "swiftdrop.security.jwks-url=http://127.0.0.1:9/jwks"
}) @AutoConfigureMockMvc @Testcontainers
class MerchantBulkIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired MerchantRepository merchants;
    @Autowired MerchantOrderRepository orders;
    @Autowired MerchantBulkStore store;
    @Autowired MerchantCsv parser;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PricingQuoteClient pricing;
    @MockitoBean ParcelClient parcels;

    @BeforeEach void clean() {
        jdbc.update("delete from merchant_bulk_uploads"); jdbc.update("delete from idempotency_keys");
        orders.deleteAll(); merchants.deleteAll();
        when(pricing.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcels.createParcel(any(), any(), any(), any())).thenAnswer(call -> new ParcelClient.InternalParcelResponse(
                UUID.randomUUID().toString(), "SD-test-" + UUID.randomUUID(), "CREATED", call.getArgument(0).toString(),
                call.getArgument(2), Instant.now(), Instant.now()));
    }
    @Test void partialValidationCreatesOnlyValidRowsWithCorrectMerchantFeeAndMapping() throws Exception {
        UUID merchant = merchant("partial");
        mvc.perform(upload(merchant, "partial-key", MerchantCsvTest.row("good") + "\nshort,row"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acceptedRows").value(1)).andExpect(jsonPath("$.rejectedRows").value(1))
                .andExpect(jsonPath("$.results[0].order.fee").value(850)).andExpect(jsonPath("$.results[1].rowNumber").value(3));
        verify(parcels, times(1)).createParcel(eq(merchant), any(), eq(new BigDecimal("850.00")), any());
        assertThat(orders.findAll().getFirst().getMerchantId()).isEqualTo(merchant);
    }
    @Test void replayReturnsOriginalResultsAndConflictingContentIsRejected() throws Exception {
        UUID merchant = merchant("replay");
        var original = mvc.perform(upload(merchant, "key", MerchantCsvTest.row("one"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(upload(merchant, "key", MerchantCsvTest.row("one"))).andExpect(content().json(original));
        mvc.perform(upload(merchant, "key", MerchantCsvTest.row("two"))).andExpect(status().isConflict());
        mvc.perform(upload(merchant, "key", "\"unclosed")).andExpect(status().isConflict());
        verify(parcels, times(1)).createParcel(any(), any(), any(), any());
    }
    @Test void duplicateRowsAndPreviouslyUsedReferencesDoNotCreateMoreParcels() throws Exception {
        UUID merchant = merchant("duplicates");
        mvc.perform(upload(merchant, "duplicates", MerchantCsvTest.row("dup") + "\n" + MerchantCsvTest.row("dup")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acceptedRows").value(0));
        verifyNoInteractions(parcels);
        mvc.perform(upload(merchant, "first", MerchantCsvTest.row("existing"))).andExpect(jsonPath("$.acceptedRows").value(1));
        mvc.perform(upload(merchant, "again", MerchantCsvTest.row("existing"))).andExpect(jsonPath("$.acceptedRows").value(0));
        verify(parcels, times(1)).createParcel(any(), any(), any(), any());
    }
    @Test void timeoutKeepsDurableReservationAcrossReplayNewUploadAndSingleOrder() throws Exception {
        UUID merchant = merchant("uncertain");
        doThrow(new ResourceAccessException("Response lost")).when(parcels).createParcel(any(), any(), any(), any());
        mvc.perform(upload(merchant, "timeout", MerchantCsvTest.row("uncertain"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].errors[0]").value(org.hamcrest.Matchers.containsString("Outcome unknown")));
        mvc.perform(upload(merchant, "timeout", MerchantCsvTest.row("uncertain"))).andExpect(status().isOk());
        mvc.perform(upload(merchant, "different-key", MerchantCsvTest.row("uncertain"))).andExpect(jsonPath("$.acceptedRows").value(0));
        var parsed = parser.parse(MerchantCsvTest.file(MerchantCsvTest.row("uncertain"))).getFirst().request();
        mvc.perform(post("/api/v1/merchant/orders").with(jwt().jwt(j -> j.subject("client").claim("role", "MERCHANT").claim("merchantId", merchant.toString())))
                .contentType("application/json").header("Idempotency-Key", store.rowKey(merchant, "timeout", 2))
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(parsed))).andExpect(status().isConflict());
        verify(parcels, times(1)).createParcel(any(), any(), any(), any());
        assertThat(orders.count()).isZero();
    }
    @Test void pricingFailureHasNoParcelSideEffectAndCanBeCorrectedInANewUpload() throws Exception {
        UUID merchant = merchant("pricing");
        when(pricing.quote(any())).thenThrow(new ResourceAccessException("Pricing unavailable"));
        mvc.perform(upload(merchant, "failed", MerchantCsvTest.row("priced"))).andExpect(jsonPath("$.acceptedRows").value(0));
        verifyNoInteractions(parcels);
        doReturn(new BigDecimal("900.00")).when(pricing).quote(any());
        mvc.perform(upload(merchant, "corrected", MerchantCsvTest.row("priced"))).andExpect(jsonPath("$.acceptedRows").value(1))
                .andExpect(jsonPath("$.results[0].order.fee").value(900));
    }
    @Test void partialParcelFailureDoesNotUndoSuccessfulRowsOrRepeatThem() throws Exception {
        UUID merchant = merchant("partial-parcel");
        doAnswer(call -> {
            CreateMerchantOrderRequest request = call.getArgument(1);
            if (request.externalReference().equals("bad")) throw new ResourceAccessException("Response lost");
            return new ParcelClient.InternalParcelResponse(UUID.randomUUID().toString(), "SD-good", "CREATED", merchant.toString(), new BigDecimal("850"), Instant.now(), Instant.now());
        }).when(parcels).createParcel(any(), any(), any(), any());
        String rows = MerchantCsvTest.row("good") + "\n" + MerchantCsvTest.row("bad");
        mvc.perform(upload(merchant, "mixed", rows)).andExpect(jsonPath("$.acceptedRows").value(1)).andExpect(jsonPath("$.rejectedRows").value(1));
        mvc.perform(upload(merchant, "mixed", rows)).andExpect(jsonPath("$.acceptedRows").value(1));
        assertThat(orders.count()).isEqualTo(1);
        verify(parcels, times(2)).createParcel(any(), any(), any(), any());
    }
    @Test void limitsAndReplayExemptionAreEnforcedPerAuthenticatedClient() throws Exception {
        UUID merchant = merchant("rate");
        for (int i = 0; i < 5; i++) mvc.perform(upload(merchant, "key-" + i, MerchantCsvTest.row("ref-" + i))).andExpect(status().isOk());
        mvc.perform(upload(merchant, "key-0", MerchantCsvTest.row("ref-0"))).andExpect(status().isOk());
        mvc.perform(upload(merchant, "sixth", MerchantCsvTest.row("sixth"))).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        mvc.perform(upload(merchant, "huge", "x".repeat(MerchantCsv.MAX_BYTES + 1))).andExpect(status().isPayloadTooLarge());
    }
    @Test void tenantKeysAndReferencesAreIsolatedAndPendingOrWrongRolesAreDenied() throws Exception {
        UUID first = merchant("first"), second = merchant("second");
        mvc.perform(upload(first, "same-key", MerchantCsvTest.row("same-ref"))).andExpect(jsonPath("$.acceptedRows").value(1));
        mvc.perform(upload(second, "same-key", MerchantCsvTest.row("same-ref"))).andExpect(jsonPath("$.acceptedRows").value(1));
        var pending = merchants.saveAndFlush(new Merchant(UUID.randomUUID(), "Pending", "Contact", "pending@test.example", "+94771234567", null, Instant.now()));
        mvc.perform(upload(pending.getId(), "pending", MerchantCsvTest.row("no"))).andExpect(status().isForbidden());
        mvc.perform(upload(first, "role", MerchantCsvTest.row("no")).with(jwt().jwt(j -> j.subject("admin").claim("role", "ADMIN"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/merchant/orders/bulk").contentType("text/csv").header("Idempotency-Key", "anonymous").content(MerchantCsvTest.file(MerchantCsvTest.row("no"))))
                .andExpect(status().isUnauthorized());
        assertThat(orders.count()).isEqualTo(2);
    }
    @Test void malformedFileDoesNotCreateAnyParcelEvenIfEarlierRowsWereValid() throws Exception {
        UUID merchant = merchant("malformed");
        mvc.perform(upload(merchant, "malformed", MerchantCsvTest.row("good") + "\n\"unclosed")).andExpect(status().isBadRequest());
        verifyNoInteractions(parcels);
        assertThat(jdbc.queryForObject("select count(*) from merchant_bulk_uploads", Integer.class)).isZero();
    }
    @Test void missingBlankAndOversizedKeysAreRejectedBeforeAnyParcel() throws Exception {
        UUID merchant = merchant("bad-keys");
        mvc.perform(upload(merchant, "", MerchantCsvTest.row("no"))).andExpect(status().isBadRequest());
        mvc.perform(upload(merchant, "x".repeat(129), MerchantCsvTest.row("no"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/merchant/orders/bulk").with(jwt().jwt(j -> j.subject("client").claim("role", "MERCHANT").claim("merchantId", merchant.toString())))
                .contentType("text/csv").content(MerchantCsvTest.file(MerchantCsvTest.row("no")))).andExpect(status().isBadRequest());
        verifyNoInteractions(parcels);
    }
    @Test void interruptedAttemptNeverPostsAgainAndLocallyCommittedOrderCanBeReconciled() throws Exception {
        UUID merchant = merchant("interrupted");
        mvc.perform(upload(merchant, "committed", MerchantCsvTest.row("committed"))).andExpect(jsonPath("$.acceptedRows").value(1));
        jdbc.update("update merchant_bulk_rows set state='ATTEMPTING', result_json=?", "{\"rowNumber\":2,\"accepted\":false,\"order\":null,\"errors\":[\"Outcome unknown\"]}");
        mvc.perform(upload(merchant, "committed", MerchantCsvTest.row("committed"))).andExpect(jsonPath("$.acceptedRows").value(1));
        jdbc.update("delete from idempotency_keys");
        mvc.perform(upload(merchant, "committed", MerchantCsvTest.row("committed"))).andExpect(jsonPath("$.acceptedRows").value(1));
        var rows = parser.parse(MerchantCsvTest.file(MerchantCsvTest.row("crashed")));
        var upload = store.initialize(merchant, "client", "crashed", "test-only-hash", rows);
        assertThat(store.claim(merchant, upload.id(), rows.getFirst(), store.rowKey(merchant, "crashed", 2))).isTrue();
        assertThat(store.report(merchant, upload.id()).results().getFirst().errors().getFirst()).contains("Outcome unknown");
        mvc.perform(upload(merchant, "new-key", MerchantCsvTest.row("crashed"))).andExpect(jsonPath("$.acceptedRows").value(0));
        verify(parcels, times(1)).createParcel(any(), any(), any(), any());
    }
    @Test void concurrentReplayDoesNotMakeAnotherParcelCall() throws Exception {
        UUID merchant = merchant("concurrent");
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(call -> {
            started.countDown();
            assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return new ParcelClient.InternalParcelResponse(UUID.randomUUID().toString(), "SD-once", "CREATED", merchant.toString(), new BigDecimal("850"), Instant.now(), Instant.now());
        }).when(parcels).createParcel(any(), any(), any(), any());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            try {
                var first = executor.submit(() -> mvc.perform(upload(merchant, "parallel", MerchantCsvTest.row("once"))).andExpect(status().isOk()).andReturn());
                assertThat(started.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var replay = executor.submit(() -> mvc.perform(upload(merchant, "parallel", MerchantCsvTest.row("once"))).andExpect(status().isOk()).andReturn());
                release.countDown();
                assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS).getResponse().getContentAsString()).contains("SD-once");
                assertThat(replay.get(10, java.util.concurrent.TimeUnit.SECONDS).getResponse().getContentAsString()).contains("SD-once");
            } finally { release.countDown(); }
        }
        verify(parcels, times(1)).createParcel(any(), any(), any(), any());
    }
    private UUID merchant(String suffix) {
        var merchant = new Merchant(UUID.randomUUID(), "Store", "Contact", suffix + "@test.example", "+94771234567", null, Instant.now());
        merchant.approve("client-" + suffix); return merchants.saveAndFlush(merchant).getId();
    }
    private MockHttpServletRequestBuilder upload(UUID merchant, String key, String rows) {
        return post("/api/v1/merchant/orders/bulk").with(jwt().jwt(j -> j.subject("client-" + merchant).claim("role", "MERCHANT").claim("merchantId", merchant.toString())))
                .contentType("text/csv").header("Idempotency-Key", key).content(MerchantCsvTest.file(rows));
    }
}
