package com.swiftdrop.integration.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "swiftdrop.webhook.allow-http=true",
        "swiftdrop.webhook.allow-local-addresses=true",
        "spring.flyway.enabled=true"
})
class WebhookIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookDispatcher dispatcher;

    @Test
    @DisplayName("Settings persist by merchant and a test uses the active secret without returning it")
    void settingsPersistAndTestUsesMerchantSecret() {
        UUID firstMerchant = UUID.randomUUID();
        UUID secondMerchant = UUID.randomUUID();
        String firstUrl = "https://127.0.0.1/first";
        String secondUrl = "https://127.0.0.1/second";

        WebhookSettingsResponse created = webhookService.updateSettings(firstMerchant,
                new UpdateWebhookSettingsRequest(firstUrl, true, List.of("PARCEL_STATUS_CHANGED"), false));
        assertThat(created.rawSecret()).isNotBlank();
        assertThat(webhookService.getSettings(firstMerchant).rawSecret()).isNull();
        assertThat(webhookService.getSettings(firstMerchant).url()).isEqualTo(firstUrl);
        assertThat(webhookService.getSettings(secondMerchant).url()).isNull();

        webhookService.updateSettings(secondMerchant,
                new UpdateWebhookSettingsRequest(secondUrl, true, List.of("PARCEL_CREATED"), false));
        assertThat(webhookService.getSettings(firstMerchant).url()).isEqualTo(firstUrl);
        assertThat(webhookService.getSettings(secondMerchant).url()).isEqualTo(secondUrl);

        WebhookSettingsResponse rotated = webhookService.updateSettings(firstMerchant,
                new UpdateWebhookSettingsRequest(firstUrl, true, List.of("PARCEL_STATUS_CHANGED"), true));
        assertThat(rotated.rawSecret()).isNotEqualTo(created.rawSecret());
        assertThat(webhookService.getSettings(firstMerchant).rawSecret()).isNull();

        when(dispatcher.dispatch(eq(firstUrl), eq(rotated.rawSecret()), anyString(), any(WebhookPayload.class)))
                .thenReturn(new WebhookDispatcher.DispatchResult(true, 204, 12, Map.of(), Map.of(),
                        null, null, null, "t=1,v1=signature", "1"));
        TestWebhookResultResponse result = webhookService.triggerTestWebhook(firstMerchant, null);
        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(204);
        verify(dispatcher).dispatch(eq(firstUrl), eq(rotated.rawSecret()), anyString(), any(WebhookPayload.class));
    }

    @Test
    @DisplayName("End-to-End: Parcel status event creates signed delivery record and delivery log shows it")
    void parcelStatusEvent_createsSignedDeliveryRecord_andIdempotent() {
        UUID merchantId = UUID.randomUUID();
        String webhookUrl = "https://api.testmerchant.com/swiftdrop/webhook";

        // 1. Setup active webhook settings for merchant
        UpdateWebhookSettingsRequest settingsReq = new UpdateWebhookSettingsRequest(
                webhookUrl,
                true,
                List.of("PARCEL_STATUS_CHANGED"),
                true
        );
        WebhookSettingsResponse settings = webhookService.updateSettings(merchantId, settingsReq);
        assertThat(settings.secretConfigured()).isTrue();
        assertThat(settings.rawSecret()).isNotNull();

        // 2. Mock dispatcher to return successful HTTP 200 response
        when(dispatcher.dispatch(eq(webhookUrl), anyString(), anyString(), any()))
                .thenReturn(new WebhookDispatcher.DispatchResult(
                        true,
                        200,
                        85,
                        Map.of("Content-Type", "application/json"),
                        Map.of("Server", "test-server"),
                        "{\"status\":\"received\"}",
                        "{\"status\":\"received\"}",
                        null,
                        "t=1729864800,v1=abcdef1234567890",
                        "1729864800"
                ));

        // 3. Process an incoming parcel.status-changed.v1 event
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        EventEnvelope event = new EventEnvelope(
                eventId.toString(),
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                2L,
                parcelId.toString(),
                "SD-20261027-100200",
                "PICKED_UP",
                "IN_TRANSIT",
                "courier-123",
                null,
                merchantId.toString(),
                null,
                null
        );

        webhookService.processParcelStatusChangedEvent(event);

        // 4. Verify delivery log record exists and shows SUCCESS
        WebhookDeliveryLogPage logs = webhookService.listDeliveries(
                merchantId, null, null, null, null, 0, 10
        );

        assertThat(logs.totalElements()).isEqualTo(1);
        WebhookDeliveryLogItem logItem = logs.content().get(0);
        assertThat(logItem.eventId()).isEqualTo(eventId);
        assertThat(logItem.status()).isEqualTo("SUCCESS");
        assertThat(logItem.httpStatusCode()).isEqualTo(200);
        assertThat(logItem.trackingNumber()).isEqualTo("SD-20261027-100200");

        // 5. Verify detailed delivery record
        WebhookDeliveryDetailResponse detail = webhookService.getDeliveryDetail(merchantId, logItem.deliveryId());
        assertThat(detail.payload()).isNotNull();
        assertThat(detail.payload().sequence()).isEqualTo(2L);
        assertThat(detail.payload().parcel().status()).isEqualTo("IN_TRANSIT");
        assertThat(detail.retryHistory()).hasSize(1);
        assertThat(detail.retryHistory().get(0).success()).isTrue();

        // 6. Verify consumer-scoped idempotency in processed_event
        assertThat(processedEventRepository.existsByEventIdAndConsumerGroup(eventId, WebhookService.CONSUMER_GROUP))
                .isTrue();

        // 7. Re-processing identical event should be deduplicated (no duplicate delivery)
        webhookService.processParcelStatusChangedEvent(event);

        WebhookDeliveryLogPage logsAfterDuplicate = webhookService.listDeliveries(
                merchantId, null, null, null, null, 0, 10
        );
        assertThat(logsAfterDuplicate.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Non-merchant parcel events are safely ignored and recorded in idempotency store")
    void nonMerchantEvent_isIgnored() {
        UUID eventId = UUID.randomUUID();
        EventEnvelope nonMerchantEvent = new EventEnvelope(
                eventId.toString(),
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                1L,
                UUID.randomUUID().toString(),
                "SD-20261027-999999",
                "CREATED",
                "PICKED_UP",
                "courier-1",
                null,
                null, // No merchantId
                "cust-1",
                null
        );

        webhookService.processParcelStatusChangedEvent(nonMerchantEvent);

        assertThat(processedEventRepository.existsByEventIdAndConsumerGroup(eventId, WebhookService.CONSUMER_GROUP))
                .isTrue();
    }

    @Test
    @DisplayName("Manual replay resets attempt count and cannot repeat a successful delivery")
    void failedDelivery_canBeReplayedOnlyUntilItSucceeds() {
        UUID merchantId = UUID.randomUUID();
        String webhookUrl = "https://api.testmerchant.com/swiftdrop/webhook";
        webhookService.updateSettings(merchantId, new UpdateWebhookSettingsRequest(
                webhookUrl, true, List.of("PARCEL_STATUS_CHANGED"), true));

        WebhookDeliveryEntity failed = new WebhookDeliveryEntity();
        failed.setDeliveryId(UUID.randomUUID());
        failed.setEventId(UUID.randomUUID());
        failed.setMerchantId(merchantId);
        failed.setEventType("PARCEL_STATUS_CHANGED");
        failed.setTargetUrl(webhookUrl);
        failed.setPayload("{}");
        failed.setStatus("FAILED");
        failed.setAttempts(5);
        failed.setNextRetryAt(Instant.now().plusSeconds(20));
        deliveryRepository.save(failed);

        when(dispatcher.dispatch(eq(webhookUrl), anyString(), eq(failed.getDeliveryId().toString()), eq("{}")))
                .thenReturn(new WebhookDispatcher.DispatchResult(
                        true, 200, 10, Map.of(), Map.of(), "ok", "ok", null, "signature", "123"));

        WebhookDeliveryDetailResponse replayed = webhookService.retryDelivery(merchantId, failed.getDeliveryId());
        assertThat(replayed.status()).isEqualTo("SUCCESS");
        assertThat(replayed.attempts()).isEqualTo(1);
        assertThat(replayed.nextRetryAt()).isNull();
        assertThat(replayed.retryHistory()).hasSize(1);

        assertThatThrownBy(() -> webhookService.retryDelivery(merchantId, failed.getDeliveryId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Only failed webhook deliveries can be retried");
        EventEnvelope delayedDlt = new EventEnvelope(failed.getEventId().toString(), "PARCEL_STATUS_CHANGED",
                Instant.now(), 2L, UUID.randomUUID().toString(), "SD-20261110-100200",
                "PICKED_UP", "IN_TRANSIT", "courier-123", null, merchantId.toString(), null, null);
        webhookService.markDltFailed(delayedDlt);
        assertThat(deliveryRepository.findById(failed.getDeliveryId()).orElseThrow().getStatus())
                .isEqualTo("SUCCESS");
        verify(dispatcher, times(1)).dispatch(eq(webhookUrl), anyString(), eq(failed.getDeliveryId().toString()), eq("{}"));
    }

    @Test
    @DisplayName("Transient failures retain one delivery through five attempts and a duplicate main event does not send")
    void transientFailures_followDeliveryStateAndAttemptLimit() {
        UUID merchantId = UUID.randomUUID();
        String webhookUrl = "http://127.0.0.1:8787/hook";
        webhookService.updateSettings(merchantId, new UpdateWebhookSettingsRequest(
                webhookUrl, true, List.of("PARCEL_STATUS_CHANGED"), true));
        EventEnvelope event = statusEvent(merchantId);
        UUID eventId = UUID.fromString(event.eventId());
        when(dispatcher.dispatch(eq(webhookUrl), anyString(), anyString(), any()))
                .thenReturn(failedResult(503));

        WebhookService.DeliveryOutcome first = webhookService.processParcelStatusChangedEvent(event);
        assertThat(first.disposition()).isEqualTo(WebhookService.DeliveryDisposition.RETRYABLE_FAILURE);
        WebhookDeliveryEntity pending = deliveryRepository
                .findFirstByEventIdAndMerchantIdOrderByCreatedAtAsc(eventId, merchantId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo("PENDING_RETRY");
        assertThat(pending.getAttempts()).isEqualTo(1);
        assertThat(pending.getNextRetryAt()).isNotNull();
        assertThat(Duration.between(pending.getLastAttemptAt(), pending.getNextRetryAt()).toMillis())
                .isBetween(1000L, 2000L);
        assertThat(processedEventRepository.existsByEventIdAndConsumerGroup(eventId, WebhookService.CONSUMER_GROUP))
                .isFalse();

        assertThat(webhookService.processParcelStatusChangedEvent(event).disposition())
                .isEqualTo(WebhookService.DeliveryDisposition.SKIPPED);
        verify(dispatcher, times(1)).dispatch(eq(webhookUrl), anyString(), anyString(), any());

        for (int attempt = 2; attempt <= 5; attempt++) {
            WebhookService.DeliveryOutcome retry = webhookService.processParcelStatusChangedEvent(event, true);
            assertThat(retry.disposition()).isEqualTo(WebhookService.DeliveryDisposition.RETRYABLE_FAILURE);
            assertThat(retry.deliveryId()).isEqualTo(first.deliveryId());
            WebhookDeliveryEntity current = deliveryRepository.findById(first.deliveryId()).orElseThrow();
            assertThat(current.getAttempts()).isEqualTo(attempt);
            assertThat(current.getStatus()).isEqualTo(attempt == 5 ? "FAILED" : "PENDING_RETRY");
            if (attempt == 5) {
                assertThat(current.getNextRetryAt()).isNull();
            } else {
                assertThat(current.getNextRetryAt()).isNotNull();
                long expectedDelay = 1000L << (attempt - 1);
                assertThat(Duration.between(current.getLastAttemptAt(), current.getNextRetryAt()).toMillis())
                        .isBetween(expectedDelay, expectedDelay + 1000L);
            }
        }

        assertThat(processedEventRepository.existsByEventIdAndConsumerGroup(eventId, WebhookService.CONSUMER_GROUP))
                .isTrue();
        assertThat(webhookService.getDeliveryDetail(merchantId, first.deliveryId()).retryHistory()).hasSize(5);
        assertThat(webhookService.listDeliveries(merchantId, null, null, null, null, 0, 10).totalElements())
                .isEqualTo(1);
        assertThat(webhookService.processParcelStatusChangedEvent(event, true).disposition())
                .isEqualTo(WebhookService.DeliveryDisposition.SKIPPED);
        verify(dispatcher, times(5)).dispatch(eq(webhookUrl), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Timeout, network, and HTTP 429 are retryable; HTTP 4xx is terminal")
    void classifiesFailuresWithoutReplayingClientErrors() {
        UUID merchantId = UUID.randomUUID();
        String webhookUrl = "http://127.0.0.1:8788/hook";
        webhookService.updateSettings(merchantId, new UpdateWebhookSettingsRequest(
                webhookUrl, true, List.of("PARCEL_STATUS_CHANGED"), true));
        when(dispatcher.dispatch(eq(webhookUrl), anyString(), anyString(), any()))
                .thenReturn(failedResult(0), failedResult(0), failedResult(429),
                        failedResult(500), failedResult(400));

        for (int status : new int[]{0, 0, 429, 500}) {
            EventEnvelope event = statusEvent(merchantId);
            WebhookService.DeliveryOutcome outcome = webhookService.processParcelStatusChangedEvent(event);
            assertThat(outcome.disposition()).isEqualTo(WebhookService.DeliveryDisposition.RETRYABLE_FAILURE);
            assertThat(deliveryRepository.findById(outcome.deliveryId()).orElseThrow().getHttpStatusCode())
                    .isEqualTo(status);
        }

        EventEnvelope clientErrorEvent = statusEvent(merchantId);
        WebhookService.DeliveryOutcome clientError = webhookService.processParcelStatusChangedEvent(clientErrorEvent);
        assertThat(clientError.disposition()).isEqualTo(WebhookService.DeliveryDisposition.TERMINAL_FAILURE);
        assertThat(deliveryRepository.findById(clientError.deliveryId()).orElseThrow().getStatus())
                .isEqualTo("FAILED");
        assertThat(processedEventRepository.existsByEventIdAndConsumerGroup(
                UUID.fromString(clientErrorEvent.eventId()), WebhookService.CONSUMER_GROUP)).isTrue();
        assertThat(webhookService.processParcelStatusChangedEvent(clientErrorEvent, true).disposition())
                .isEqualTo(WebhookService.DeliveryDisposition.SKIPPED);
        verify(dispatcher, times(5)).dispatch(eq(webhookUrl), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Deactivating an endpoint cancels a pending retry without another HTTP request")
    void deactivatedEndpoint_cancelsPendingRetry() {
        UUID merchantId = UUID.randomUUID();
        String webhookUrl = "http://127.0.0.1:8791/hook";
        webhookService.updateSettings(merchantId, new UpdateWebhookSettingsRequest(
                webhookUrl, true, List.of("PARCEL_STATUS_CHANGED"), true));
        when(dispatcher.dispatch(eq(webhookUrl), anyString(), anyString(), any()))
                .thenReturn(failedResult(503));
        EventEnvelope event = statusEvent(merchantId);
        WebhookService.DeliveryOutcome first = webhookService.processParcelStatusChangedEvent(event);

        webhookService.updateSettings(merchantId, new UpdateWebhookSettingsRequest(
                webhookUrl, false, List.of("PARCEL_STATUS_CHANGED"), false));
        assertThat(webhookService.processParcelStatusChangedEvent(event, true).disposition())
                .isEqualTo(WebhookService.DeliveryDisposition.SKIPPED);
        WebhookDeliveryEntity delivery = deliveryRepository.findById(first.deliveryId()).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo("CANCELLED");
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getNextRetryAt()).isNull();
        verify(dispatcher, times(1)).dispatch(eq(webhookUrl), anyString(), anyString(), any());
    }

    private static EventEnvelope statusEvent(UUID merchantId) {
        return new EventEnvelope(UUID.randomUUID().toString(), "PARCEL_STATUS_CHANGED", Instant.now(),
                2L, UUID.randomUUID().toString(), "SD-20261110-100200", "PICKED_UP", "IN_TRANSIT",
                "courier-123", null, merchantId.toString(), null, null);
    }

    private static WebhookDispatcher.DispatchResult failedResult(int status) {
        return new WebhookDispatcher.DispatchResult(false, status, 10, Map.of(), Map.of(), null,
                null, status == 0 ? "Webhook transport failure: IOException" : "Target returned HTTP " + status,
                null, null);
    }
}
