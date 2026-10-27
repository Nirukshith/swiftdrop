package com.swiftdrop.integration.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookService webhookService;

    private final UUID merchantId = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d");

    @Test
    @WithMockUser
    @DisplayName("GET /settings returns configured webhook settings")
    void getSettings_success() throws Exception {
        WebhookSettingsResponse mockResponse = new WebhookSettingsResponse(
                merchantId,
                "https://api.merchant.com/webhook",
                true,
                List.of("PARCEL_STATUS_CHANGED"),
                true,
                "whsec_••••••••••••••••3f8a",
                null,
                Instant.now(),
                Instant.now()
        );

        when(webhookService.getSettings(merchantId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/merchant/webhooks/settings")
                        .header("X-Merchant-Id", merchantId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.url").value("https://api.merchant.com/webhook"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.secretPreview").value("whsec_••••••••••••••••3f8a"));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /settings updates destination URL and returns rawSecret on generation")
    void updateSettings_success() throws Exception {
        UpdateWebhookSettingsRequest request = new UpdateWebhookSettingsRequest(
                "https://api.merchant.com/new-webhook",
                true,
                List.of("PARCEL_CREATED", "PARCEL_STATUS_CHANGED"),
                true
        );

        WebhookSettingsResponse mockResponse = new WebhookSettingsResponse(
                merchantId,
                "https://api.merchant.com/new-webhook",
                true,
                List.of("PARCEL_CREATED", "PARCEL_STATUS_CHANGED"),
                true,
                "whsec_••••••••••••••••99ab",
                "whsec_full_secret_returned_on_rotation_99ab",
                Instant.now(),
                Instant.now()
        );

        when(webhookService.updateSettings(eq(merchantId), any(UpdateWebhookSettingsRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(put("/api/v1/merchant/webhooks/settings")
                        .with(csrf())
                        .header("X-Merchant-Id", merchantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://api.merchant.com/new-webhook"))
                .andExpect(jsonPath("$.rawSecret").value("whsec_full_secret_returned_on_rotation_99ab"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /test dispatches test event and returns 200 on target success")
    void triggerTest_success() throws Exception {
        TestWebhookRequest request = new TestWebhookRequest(
                "https://webhook.site/test-uuid",
                "TEST_EVENT"
        );

        TestWebhookResultResponse mockResult = new TestWebhookResultResponse(
                true,
                200,
                120,
                Instant.now(),
                "https://webhook.site/test-uuid",
                "t=1729864800,v1=4d7f508a",
                "1729864800",
                "{\"status\":\"ok\"}",
                null
        );

        when(webhookService.triggerTestWebhook(eq(merchantId), any(TestWebhookRequest.class)))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/merchant/webhooks/test")
                        .with(csrf())
                        .header("X-Merchant-Id", merchantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.statusCode").value(200));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /test returns 502 Bad Gateway when target fails")
    void triggerTest_targetFailure_returns502() throws Exception {
        TestWebhookRequest request = new TestWebhookRequest(
                "https://webhook.site/failed",
                "TEST_EVENT"
        );

        TestWebhookResultResponse mockResult = new TestWebhookResultResponse(
                false,
                500,
                350,
                Instant.now(),
                "https://webhook.site/failed",
                "t=1729864800,v1=4d7f508a",
                "1729864800",
                "Internal Server Error",
                "Target returned HTTP 500"
        );

        when(webhookService.triggerTestWebhook(eq(merchantId), any(TestWebhookRequest.class)))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/merchant/webhooks/test")
                        .with(csrf())
                        .header("X-Merchant-Id", merchantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.statusCode").value(500));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /deliveries returns paginated log items")
    void listDeliveries_success() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        WebhookDeliveryLogItem item = new WebhookDeliveryLogItem(
                deliveryId,
                eventId,
                "PARCEL_STATUS_CHANGED",
                "SD-20261024-891023",
                "https://api.merchant.com/webhook",
                "SUCCESS",
                200,
                1,
                Instant.now(),
                null,
                null,
                Instant.now()
        );

        WebhookDeliveryLogPage page = new WebhookDeliveryLogPage(
                List.of(item),
                1L,
                1,
                0,
                20
        );

        when(webhookService.listDeliveries(eq(merchantId), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/merchant/webhooks/deliveries")
                        .header("X-Merchant-Id", merchantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].deliveryId").value(deliveryId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /deliveries/{deliveryId} returns delivery detail")
    void getDeliveryDetail_success() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        WebhookDeliveryDetailResponse detail = new WebhookDeliveryDetailResponse(
                deliveryId,
                eventId,
                "PARCEL_STATUS_CHANGED",
                "SUCCESS",
                "https://api.merchant.com/webhook",
                1,
                200,
                Map.of("Content-Type", "application/json"),
                null,
                Map.of("Server", "nginx"),
                "{\"received\":true}",
                Instant.now(),
                null,
                List.of(new DeliveryAttemptRecord(1, Instant.now(), 200, 110, true, null)),
                Instant.now()
        );

        when(webhookService.getDeliveryDetail(merchantId, deliveryId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/merchant/webhooks/deliveries/" + deliveryId)
                        .header("X-Merchant-Id", merchantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryId").value(deliveryId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.retryHistory[0].httpStatusCode").value(200));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /deliveries/{deliveryId} returns 404 when not found")
    void getDeliveryDetail_notFound() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        when(webhookService.getDeliveryDetail(merchantId, deliveryId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook delivery not found"));

        mockMvc.perform(get("/api/v1/merchant/webhooks/deliveries/" + deliveryId)
                        .header("X-Merchant-Id", merchantId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /deliveries/{deliveryId}/retry triggers retry and returns 202 Accepted")
    void retryDelivery_success() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        WebhookDeliveryDetailResponse updated = new WebhookDeliveryDetailResponse(
                deliveryId,
                UUID.randomUUID(),
                "PARCEL_STATUS_CHANGED",
                "SUCCESS",
                "https://api.merchant.com/webhook",
                2,
                200,
                Map.of(),
                null,
                Map.of(),
                "ok",
                Instant.now(),
                null,
                List.of(),
                Instant.now()
        );

        when(webhookService.retryDelivery(merchantId, deliveryId)).thenReturn(updated);

        mockMvc.perform(post("/api/v1/merchant/webhooks/deliveries/" + deliveryId + "/retry")
                        .with(csrf())
                        .header("X-Merchant-Id", merchantId.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.attempts").value(2))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
