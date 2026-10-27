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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
}
