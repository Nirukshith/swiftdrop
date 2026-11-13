package com.swiftdrop.integration.merchant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MerchantOrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired MerchantRepository merchantRepository;
    @Autowired MerchantOrderRepository orderRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean PricingQuoteClient pricingQuoteClient;
    @MockitoBean ParcelClient parcelClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from idempotency_keys");
        orderRepository.deleteAll();
        merchantRepository.deleteAll();
        clearInvocations(pricingQuoteClient, parcelClient);
    }

    @Test
    void createOrderUsesClaimAndPersistsQuoteAndParcelMapping() throws Exception {
        UUID merchantId = approvedMerchant("create@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(merchantId), any(), eq(new BigDecimal("850.00")), any()))
                .thenReturn(parcel(merchantId, "5ad84fda-20a8-4412-81de-3a8728f10d3d", "SD-20261028-0001"));

        mockMvc.perform(createRequest(merchantId, "create-key-1", requestJson("order-100")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.parcelId").value("5ad84fda-20a8-4412-81de-3a8728f10d3d"))
                .andExpect(jsonPath("$.trackingNumber").value("SD-20261028-0001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.fee").value(850.0));

        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isEqualTo(1);
        verify(parcelClient).createParcel(eq(merchantId), any(), eq(new BigDecimal("850.00")), any());
    }

    @Test
    void sameMerchantKeyAndSameBodyReturnsStoredResponseWithoutCreatingAnotherParcel() throws Exception {
        UUID merchantId = approvedMerchant("replay@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(merchantId), any(), eq(new BigDecimal("850.00")), any()))
                .thenReturn(parcel(merchantId, "b3b03c0e-fdb4-47df-b479-ff41e8b11934", "SD-20261028-0002"));
        String body = requestJson("order-200");

        mockMvc.perform(createRequest(merchantId, "replay-key", body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists());
        String expectedOrderId = orderRepository.findAll().getFirst().getOrderId().toString();

        mockMvc.perform(createRequest(merchantId, "replay-key", body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(expectedOrderId))
                .andExpect(jsonPath("$.trackingNumber").value("SD-20261028-0002"));

        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isEqualTo(1);
        verify(parcelClient, org.mockito.Mockito.times(1))
                .createParcel(eq(merchantId), any(), eq(new BigDecimal("850.00")), any());
    }

    @Test
    void sameMerchantKeyWithDifferentBodyReturnsConflict() throws Exception {
        UUID merchantId = approvedMerchant("conflict@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(merchantId), any(), eq(new BigDecimal("850.00")), any()))
                .thenReturn(parcel(merchantId, "68e927c7-57fd-4a9d-9f93-f2adfe9dd716", "SD-20261028-0003"));

        mockMvc.perform(createRequest(merchantId, "conflict-key", requestJson("order-300")))
                .andExpect(status().isCreated());
        mockMvc.perform(createRequest(merchantId, "conflict-key", requestJson("order-301")))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isEqualTo(1);
        verify(parcelClient, org.mockito.Mockito.times(1))
                .createParcel(eq(merchantId), any(), eq(new BigDecimal("850.00")), any());
    }

    @Test
    void idempotencyKeysAreScopedToMerchantClaim() throws Exception {
        UUID firstMerchant = approvedMerchant("first@example.test");
        UUID secondMerchant = approvedMerchant("second@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(firstMerchant), any(), eq(new BigDecimal("850.00")), any()))
                .thenReturn(parcel(firstMerchant, "a8db1755-0ce5-49c5-a66f-cf56232efc19", "SD-20261028-0004"));
        when(parcelClient.createParcel(eq(secondMerchant), any(), eq(new BigDecimal("850.00")), any()))
                .thenReturn(parcel(secondMerchant, "d60d120e-4803-4f63-8f3f-a9010bef4536", "SD-20261028-0005"));

        mockMvc.perform(createRequest(firstMerchant, "shared-key", requestJson("same-reference")))
                .andExpect(status().isCreated());
        mockMvc.perform(createRequest(secondMerchant, "shared-key", requestJson("same-reference")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelId").value("d60d120e-4803-4f63-8f3f-a9010bef4536"));

        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isEqualTo(2);
    }

    @Test
    void cancelOrderUsesM1StatusContractAndPersistsCancelledStatus() throws Exception {
        UUID merchantId = approvedMerchant("cancel@example.test");
        UUID parcelId = UUID.fromString("247f52b9-1658-4c06-a68c-a3c5405a0932");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(merchantId), any(), eq(new BigDecimal("850.00")), any()))
                .thenReturn(parcel(merchantId, parcelId.toString(), "SD-20261028-0006"));
        mockMvc.perform(createRequest(merchantId, "cancel-create", requestJson("order-cancel")))
                .andExpect(status().isCreated());
        UUID orderId = orderRepository.findAll().getFirst().getOrderId();
        when(parcelClient.cancelParcel(eq(parcelId), eq("Duplicate order"), any()))
                .thenReturn(new ParcelClient.InternalParcelResponse(parcelId.toString(), "SD-20261028-0006",
                        "CANCELLED", merchantId.toString(), new BigDecimal("850.00"),
                        Instant.parse("2026-10-28T10:00:00Z"), Instant.parse("2026-10-28T10:05:00Z")));

        mockMvc.perform(cancelRequest(merchantId, orderId, "cancel-key", "{\"reason\":\"Duplicate order\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        org.assertj.core.api.Assertions.assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(MerchantOrderStatus.CANCELLED);
        verify(parcelClient).cancelParcel(eq(parcelId), eq("Duplicate order"), any());
    }

    @Test
    void sameCancellationKeyAndBodyReturnsStoredResponse() throws Exception {
        UUID merchantId = approvedMerchant("cancel-replay@example.test");
        UUID parcelId = UUID.fromString("5d045ed0-88d3-4de9-b1e5-6254da4b08ea");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(merchantId), any(), eq(new BigDecimal("850.00")), any()))
                .thenReturn(parcel(merchantId, parcelId.toString(), "SD-20261028-0007"));
        mockMvc.perform(createRequest(merchantId, "cancel-replay-create", requestJson("order-cancel-replay")))
                .andExpect(status().isCreated());
        UUID orderId = orderRepository.findAll().getFirst().getOrderId();
        when(parcelClient.cancelParcel(eq(parcelId), eq("Changed mind"), any()))
                .thenReturn(new ParcelClient.InternalParcelResponse(parcelId.toString(), "SD-20261028-0007",
                        "CANCELLED", merchantId.toString(), new BigDecimal("850.00"),
                        Instant.parse("2026-10-28T10:00:00Z"), Instant.parse("2026-10-28T10:05:00Z")));
        String body = "{\"reason\":\"Changed mind\"}";

        mockMvc.perform(cancelRequest(merchantId, orderId, "cancel-replay-key", body)).andExpect(status().isOk());
        mockMvc.perform(cancelRequest(merchantId, orderId, "cancel-replay-key", body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(parcelClient, org.mockito.Mockito.times(1)).cancelParcel(eq(parcelId), eq("Changed mind"), any());
    }

    @Test
    void cancellationKeyWithDifferentReasonReturnsConflict() throws Exception {
        UUID merchantId = approvedMerchant("cancel-conflict@example.test");
        UUID parcelId = UUID.fromString("89b04e69-94dd-499b-bb0d-13aa16a42242");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(merchantId), any(), eq(new BigDecimal("850.00")), any()))
                .thenReturn(parcel(merchantId, parcelId.toString(), "SD-20261028-0008"));
        mockMvc.perform(createRequest(merchantId, "cancel-conflict-create", requestJson("order-cancel-conflict")))
                .andExpect(status().isCreated());
        UUID orderId = orderRepository.findAll().getFirst().getOrderId();
        when(parcelClient.cancelParcel(eq(parcelId), eq("Reason one"), any()))
                .thenReturn(new ParcelClient.InternalParcelResponse(parcelId.toString(), "SD-20261028-0008",
                        "CANCELLED", merchantId.toString(), new BigDecimal("850.00"),
                        Instant.parse("2026-10-28T10:00:00Z"), Instant.parse("2026-10-28T10:05:00Z")));

        mockMvc.perform(cancelRequest(merchantId, orderId, "cancel-conflict-key", "{\"reason\":\"Reason one\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(cancelRequest(merchantId, orderId, "cancel-conflict-key", "{\"reason\":\"Reason two\"}"))
                .andExpect(status().isConflict());
        verify(parcelClient, org.mockito.Mockito.times(1)).cancelParcel(eq(parcelId), eq("Reason one"), any());
    }

    @Test
    void cancellationKeyCannotReplayAResponseForAnotherOrder() throws Exception {
        UUID merchantId = approvedMerchant("cancel-target@example.test");
        MerchantOrderEntity first = storedOrder(merchantId, MerchantOrderStatus.CREATED);
        MerchantOrderEntity second = storedOrder(merchantId, MerchantOrderStatus.CREATED);
        when(parcelClient.cancelParcel(eq(first.getParcelId()), eq("Changed mind"), any()))
                .thenReturn(cancelledParcel(first));
        String body = "{\"reason\":\"Changed mind\"}";
        mockMvc.perform(cancelRequest(merchantId, first.getOrderId(), "target-key", body))
                .andExpect(status().isOk());
        mockMvc.perform(cancelRequest(merchantId, second.getOrderId(), "target-key", body))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(orderRepository.findById(second.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(MerchantOrderStatus.CREATED);
        verify(parcelClient, org.mockito.Mockito.times(1)).cancelParcel(any(), any(), any());
    }

    @Test
    void ownershipIsCheckedBeforeReplayingACancellation() throws Exception {
        UUID merchantId = approvedMerchant("owner@example.test");
        UUID otherMerchant = approvedMerchant("other-owner@example.test");
        MerchantOrderEntity own = storedOrder(merchantId, MerchantOrderStatus.CREATED);
        MerchantOrderEntity foreign = storedOrder(otherMerchant, MerchantOrderStatus.CREATED);
        when(parcelClient.cancelParcel(eq(own.getParcelId()), any(), any())).thenReturn(cancelledParcel(own));
        mockMvc.perform(cancelRequest(merchantId, own.getOrderId(), "owner-key", null))
                .andExpect(status().isOk());
        mockMvc.perform(cancelRequest(merchantId, foreign.getOrderId(), "owner-key", null))
                .andExpect(status().isNotFound());
        mockMvc.perform(cancelRequest(otherMerchant, own.getOrderId(), "new-key", null))
                .andExpect(status().isNotFound());
        verify(parcelClient, org.mockito.Mockito.times(1)).cancelParcel(any(), any(), any());
    }

    @Test
    void staleCreatedMappingCannotCancelAPickedUpParcel() throws Exception {
        UUID merchantId = approvedMerchant("stale@example.test");
        MerchantOrderEntity order = storedOrder(merchantId, MerchantOrderStatus.CREATED);
        when(parcelClient.cancelParcel(eq(order.getParcelId()), any(), any()))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.CONFLICT));
        mockMvc.perform(cancelRequest(merchantId, order.getOrderId(), "stale-key", null))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(MerchantOrderStatus.CREATED);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select count(*) from idempotency_keys", Integer.class)).isZero();
    }

    @Test
    void localPickedUpStateRejectsCancellationWithoutCallingParcelService() throws Exception {
        UUID merchantId = approvedMerchant("picked-up@example.test");
        MerchantOrderEntity order = storedOrder(merchantId, MerchantOrderStatus.PICKED_UP);
        mockMvc.perform(cancelRequest(merchantId, order.getOrderId(), "picked-up-key", null))
                .andExpect(status().isConflict());
        org.mockito.Mockito.verifyNoInteractions(parcelClient);
    }

    @Test
    void cancellationFailureRollsBackTheKeySoTheSameRequestCanRetry() throws Exception {
        UUID merchantId = approvedMerchant("retry-cancel@example.test");
        MerchantOrderEntity order = storedOrder(merchantId, MerchantOrderStatus.CREATED);
        when(parcelClient.cancelParcel(eq(order.getParcelId()), any(), any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection interrupted"))
                .thenReturn(cancelledParcel(order));
        mockMvc.perform(cancelRequest(merchantId, order.getOrderId(), "retry-key", null))
                .andExpect(status().isBadGateway());
        mockMvc.perform(cancelRequest(merchantId, order.getOrderId(), "retry-key", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(parcelClient, org.mockito.Mockito.times(2)).cancelParcel(any(), any(), any());
    }

    @Test
    void pendingMerchantCannotCreateOrdersEvenWithAMerchantRoleClaim() throws Exception {
        Merchant pending = merchantRepository.saveAndFlush(new Merchant(UUID.randomUUID(), "Pending Store", "Contact",
                "pending-order@example.test", "+94771234567", null, Instant.now()));
        mockMvc.perform(createRequest(pending.getId(), "pending-key", requestJson("pending-order")))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(pricingQuoteClient, parcelClient);
    }

    @Test
    void semanticallyEquivalentJsonReturnsTheEntireOriginalResponse() throws Exception {
        UUID merchantId = approvedMerchant("canonical@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(merchantId), any(), any(), any()))
                .thenReturn(parcel(merchantId, UUID.randomUUID().toString(), "SD-20261110-0100"));
        String body = requestJson("canonical-order");
        String first = mockMvc.perform(createRequest(merchantId, "canonical-key", body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = mockMvc.perform(createRequest(merchantId, "canonical-key", body.replace("1.5", "1.500")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        org.assertj.core.api.Assertions.assertThat(json.readTree(replay)).isEqualTo(json.readTree(first));
        verify(parcelClient, org.mockito.Mockito.times(1)).createParcel(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "blank-id", "invalid-id", "blank-tracking", "invalid-status", "missing-time", "foreign-merchant", "missing-merchant", "negative-fee"})
    void invalidParcelResponseDoesNotPersistAnOrderOrPoisonItsKey(String variant) throws Exception {
        UUID merchantId = approvedMerchant("parcel-validation@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        ParcelClient.InternalParcelResponse response = "missing".equals(variant) ? null : new ParcelClient.InternalParcelResponse(
                "blank-id".equals(variant) ? " " : "invalid-id".equals(variant) ? "not-a-uuid" : UUID.randomUUID().toString(),
                "blank-tracking".equals(variant) ? " " : "SD-20261112-0001",
                "invalid-status".equals(variant) ? "UNKNOWN" : "CREATED",
                "foreign-merchant".equals(variant) ? UUID.randomUUID().toString() : "missing-merchant".equals(variant) ? null : merchantId.toString(),
                new BigDecimal("negative-fee".equals(variant) ? "-1" : "850.00"),
                "missing-time".equals(variant) ? null : Instant.now(), Instant.now());
        when(parcelClient.createParcel(eq(merchantId), any(), any(), any())).thenReturn(response);
        mockMvc.perform(createRequest(merchantId, "invalid-parcel-key", requestJson("invalid-parcel")))
                .andExpect(status().isBadGateway());
        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select count(*) from idempotency_keys", Integer.class)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"400,422", "422,422", "409,409", "500,502", "503,502"})
    void parcelHttpFailuresUseTheMerchantContractAndRollBackTheKey(int downstreamStatus, int expectedStatus) throws Exception {
        UUID merchantId = approvedMerchant("parcel-http-failure@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        org.springframework.http.HttpStatus code = org.springframework.http.HttpStatus.valueOf(downstreamStatus);
        org.springframework.web.client.HttpStatusCodeException failure = code.is5xxServerError()
                ? new org.springframework.web.client.HttpServerErrorException(code)
                : new org.springframework.web.client.HttpClientErrorException(code);
        when(parcelClient.createParcel(eq(merchantId), any(), any(), any())).thenThrow(failure);
        mockMvc.perform(createRequest(merchantId, "parcel-http-key", requestJson("parcel-http-failure")))
                .andExpect(status().is(expectedStatus));
        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select count(*) from idempotency_keys", Integer.class)).isZero();
    }

    @Test
    void failedParcelCreationCanRetryWithTheSameKeyAndThenReplay() throws Exception {
        UUID merchantId = approvedMerchant("parcel-retry@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(merchantId), any(), any(), any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Parcel unavailable"))
                .thenReturn(parcel(merchantId, UUID.randomUUID().toString(), "SD-20261112-0002"));
        String body = requestJson("parcel-retry");
        mockMvc.perform(createRequest(merchantId, "parcel-retry-key", body)).andExpect(status().isBadGateway());
        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isZero();
        mockMvc.perform(createRequest(merchantId, "parcel-retry-key", body)).andExpect(status().isCreated());
        mockMvc.perform(createRequest(merchantId, "parcel-retry-key", body)).andExpect(status().isCreated());
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(parcelClient, org.mockito.Mockito.times(2)).createParcel(eq(merchantId), any(), any(), keys.capture());
        org.assertj.core.api.Assertions.assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void failedQuoteDoesNotCreateAParcelAndDoesNotPoisonTheKey() throws Exception {
        UUID merchantId = approvedMerchant("quote-retry@example.test");
        when(pricingQuoteClient.quote(any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Pricing unavailable"))
                .thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(merchantId), any(), any(), any()))
                .thenReturn(parcel(merchantId, UUID.randomUUID().toString(), "SD-20261112-0003"));
        mockMvc.perform(createRequest(merchantId, "quote-key", requestJson("quote-retry"))).andExpect(status().isBadGateway());
        org.mockito.Mockito.verifyNoInteractions(parcelClient);
        mockMvc.perform(createRequest(merchantId, "quote-key", requestJson("quote-retry"))).andExpect(status().isCreated());
    }

    @Test
    void ambiguousCreatePinsTheQuoteAndReservesTheReferenceAcrossRequests() throws Exception {
        UUID merchant = approvedMerchant("ambiguous-create@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"), new BigDecimal("1350.00"));
        when(parcelClient.createParcel(eq(merchant), any(), any(), any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Response lost after commit"))
                .thenReturn(parcel(merchant, UUID.randomUUID().toString(), "SD-RECOVERED"));
        String body = requestJson("ambiguous-reference");
        mockMvc.perform(createRequest(merchant, "original-key", body)).andExpect(status().isBadGateway());
        mockMvc.perform(createRequest(merchant, "new-key", body)).andExpect(status().isConflict());
        mockMvc.perform(createRequest(merchant, "original-key", requestJson("changed-reference")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/merchant/orders/bulk")
                .with(jwt().jwt(token -> token.subject("client-123").claim("role", "MERCHANT")
                        .claim("merchantId", merchant.toString())))
                .header("Idempotency-Key", "bulk-cannot-steal")
                .contentType("text/csv").content(MerchantCsvTest.file(MerchantCsvTest.row("ambiguous-reference"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acceptedRows").value(0));
        mockMvc.perform(createRequest(merchant, "original-key", body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fee").value(850));
        mockMvc.perform(createRequest(merchant, "original-key", body)).andExpect(status().isCreated());
        verify(pricingQuoteClient).quote(any());
        var fees = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(parcelClient, org.mockito.Mockito.times(2)).createParcel(eq(merchant), any(), fees.capture(), keys.capture());
        org.assertj.core.api.Assertions.assertThat(fees.getAllValues()).containsOnly(new BigDecimal("850.00"));
        org.assertj.core.api.Assertions.assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void explicitParcelValidationFailureReleasesTheReferenceForACorrectedRequest() throws Exception {
        UUID merchant = approvedMerchant("correctable@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"));
        when(parcelClient.createParcel(eq(merchant), any(), any(), any()))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY))
                .thenReturn(parcel(merchant, UUID.randomUUID().toString(), "SD-CORRECTED"));
        mockMvc.perform(createRequest(merchant, "invalid-key", requestJson("correctable")))
                .andExpect(status().isUnprocessableEntity());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select count(*) from merchant_order_attempts", Integer.class))
                .isZero();
        mockMvc.perform(createRequest(merchant, "corrected-key", requestJson("correctable")))
                .andExpect(status().isCreated());
    }

    @Test
    void localPersistenceFailureAfterParcelSuccessRetainsTheDurableQuote() throws Exception {
        UUID merchant = approvedMerchant("order-local-failure@example.test");
        when(pricingQuoteClient.quote(any())).thenReturn(new BigDecimal("850.00"), new BigDecimal("1350.00"));
        when(parcelClient.createParcel(eq(merchant), any(), any(), any()))
                .thenReturn(parcel(merchant, UUID.randomUUID().toString(), "SD-LOCAL-RECOVERY"));
        jdbcTemplate.execute("CREATE FUNCTION reject_test_order() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'simulated insert failure' USING ERRCODE='23505'; END $$");
        jdbcTemplate.execute("CREATE TRIGGER test_order_failure BEFORE INSERT ON merchant_orders FOR EACH ROW EXECUTE FUNCTION reject_test_order()");
        try {
            mockMvc.perform(createRequest(merchant, "local-failure", requestJson("local-failure")))
                    .andExpect(status().isConflict());
            org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isZero();
            org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select count(*) from merchant_order_attempts", Integer.class))
                    .isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select count(*) from idempotency_keys", Integer.class))
                    .isZero();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER test_order_failure ON merchant_orders");
            jdbcTemplate.execute("DROP FUNCTION reject_test_order()");
        }
        mockMvc.perform(createRequest(merchant, "local-failure", requestJson("local-failure")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fee").value(850));
        verify(pricingQuoteClient).quote(any());
        verify(parcelClient, org.mockito.Mockito.times(2)).createParcel(eq(merchant), any(), eq(new BigDecimal("850.00")), any());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select count(*) from merchant_order_attempts", Integer.class))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign-merchant", "missing-merchant", "wrong-parcel", "wrong-status", "invalid-id"})
    void invalidCancellationResponsePreservesTheOrderAndAllowsSameKeyRetry(String variant) throws Exception {
        UUID merchant = approvedMerchant("cancel-validation@example.test");
        var order = storedOrder(merchant, MerchantOrderStatus.CREATED);
        var invalid = new ParcelClient.InternalParcelResponse(
                "invalid-id".equals(variant) ? "invalid" : "wrong-parcel".equals(variant) ? UUID.randomUUID().toString() : order.getParcelId().toString(),
                order.getTrackingNumber(), "wrong-status".equals(variant) ? "PICKED_UP" : "CANCELLED",
                "foreign-merchant".equals(variant) ? UUID.randomUUID().toString() : "missing-merchant".equals(variant) ? null : merchant.toString(),
                order.getFee(), order.getCreatedAt(), Instant.now());
        when(parcelClient.cancelParcel(eq(order.getParcelId()), any(), any()))
                .thenReturn(invalid, cancelledParcel(order));
        mockMvc.perform(cancelRequest(merchant, order.getOrderId(), "cancel-validation", "{\"reason\":\"Mistake\"}"))
                .andExpect(status().isBadGateway());
        org.assertj.core.api.Assertions.assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(MerchantOrderStatus.CREATED);
        mockMvc.perform(cancelRequest(merchant, order.getOrderId(), "cancel-validation", "{\"reason\":\"Mistake\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void rejectedMerchantCannotUseMerchantRoleClaimsToCreateAnOrder() throws Exception {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Rejected Store", "Contact", "rejected-order@example.test",
                "+94771234567", null, Instant.now());
        merchant.reject("Business not verified");
        merchantRepository.saveAndFlush(merchant);
        mockMvc.perform(createRequest(merchant.getId(), "rejected-key", requestJson("rejected")))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(pricingQuoteClient, parcelClient);
    }

    @Test
    void invalidInputAndMissingIdempotencyKeyNeverReachDownstreamServices() throws Exception {
        UUID merchantId = approvedMerchant("invalid-input@example.test");
        mockMvc.perform(createRequest(merchantId, "invalid-input", requestJson("input").replace("1.5", "0")))
                .andExpect(status().isBadRequest());
        var request = createRequest(merchantId, "unused", requestJson("input"));
        request.with(mock -> { mock.removeHeader("Idempotency-Key"); return mock; });
        mockMvc.perform(request).andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(pricingQuoteClient, parcelClient);
    }

    private MerchantOrderEntity storedOrder(UUID merchantId, MerchantOrderStatus status) {
        return orderRepository.saveAndFlush(new MerchantOrderEntity(UUID.randomUUID(), merchantId, UUID.randomUUID(),
                "SD-" + UUID.randomUUID(), "ref-" + UUID.randomUUID(), status, new BigDecimal("850.00"), Instant.now()));
    }

    private ParcelClient.InternalParcelResponse cancelledParcel(MerchantOrderEntity order) {
        return new ParcelClient.InternalParcelResponse(order.getParcelId().toString(), order.getTrackingNumber(),
                "CANCELLED", order.getMerchantId().toString(), order.getFee(), order.getCreatedAt(), Instant.now());
    }

    private UUID approvedMerchant(String email) {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Store", "Contact", email,
                "+94771234567", null, Instant.now());
        merchant.approve("client-" + UUID.randomUUID());
        return merchantRepository.saveAndFlush(merchant).getId();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest(
            UUID merchantId, String idempotencyKey, String body) {
        return post("/api/v1/merchant/orders")
                .with(jwt().jwt(token -> token.subject("client-123")
                        .claim("role", "MERCHANT")
                        .claim("roles", java.util.List.of("ROLE_MERCHANT"))
                        .claim("merchantId", merchantId.toString())))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private ParcelClient.InternalParcelResponse parcel(UUID merchantId, String id, String trackingNumber) {
        return new ParcelClient.InternalParcelResponse(id, trackingNumber, "CREATED", merchantId.toString(),
                new BigDecimal("850.00"), Instant.parse("2026-10-28T10:00:00Z"),
                Instant.parse("2026-10-28T10:00:00Z"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder cancelRequest(
            UUID merchantId, UUID orderId, String idempotencyKey, String body) {
        var request = post("/api/v1/merchant/orders/{orderId}/cancel", orderId)
                .with(jwt().jwt(token -> token.subject("client-123")
                        .claim("role", "MERCHANT")
                        .claim("roles", java.util.List.of("ROLE_MERCHANT"))
                        .claim("merchantId", merchantId.toString())))
                .header("Idempotency-Key", idempotencyKey);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request;
    }

    private String requestJson(String externalReference) {
        return """
                {"externalReference":"%s","recipientName":"Asha Perera","recipientPhone":"+94771234567",
                 "pickupAddress":{"line1":"10 Main Road","city":"Colombo","district":"Colombo","postalCode":"00100"},
                 "dropoffAddress":{"line1":"20 Lake Road","city":"Gampaha","district":"Gampaha","postalCode":"11000"},
                 "size":"SMALL","weightKg":1.5}
                """.formatted(externalReference);
    }
}
