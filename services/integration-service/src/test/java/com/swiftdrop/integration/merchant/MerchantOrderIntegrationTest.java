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
                .thenReturn(parcel("5ad84fda-20a8-4412-81de-3a8728f10d3d", "SD-20261028-0001"));

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
                .thenReturn(parcel("b3b03c0e-fdb4-47df-b479-ff41e8b11934", "SD-20261028-0002"));
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
                .thenReturn(parcel("68e927c7-57fd-4a9d-9f93-f2adfe9dd716", "SD-20261028-0003"));

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
                .thenReturn(parcel("a8db1755-0ce5-49c5-a66f-cf56232efc19", "SD-20261028-0004"));
        when(parcelClient.createParcel(eq(secondMerchant), any(), eq(new BigDecimal("850.00")), any()))
                .thenReturn(parcel("d60d120e-4803-4f63-8f3f-a9010bef4536", "SD-20261028-0005"));

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
                .thenReturn(parcel(parcelId.toString(), "SD-20261028-0006"));
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
                .thenReturn(parcel(parcelId.toString(), "SD-20261028-0007"));
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
                .thenReturn(parcel(parcelId.toString(), "SD-20261028-0008"));
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

    private ParcelClient.InternalParcelResponse parcel(String id, String trackingNumber) {
        return new ParcelClient.InternalParcelResponse(id, trackingNumber, "CREATED", null,
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
