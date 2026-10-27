package com.swiftdrop.integration.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WebhookKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookKafkaConsumer.class);

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public WebhookKafkaConsumer(WebhookService webhookService, ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${swiftdrop.kafka.topics.parcel-status-changed:parcel.status-changed.v1}",
            groupId = "integration-webhook"
    )
    public void onParcelStatusChanged(String payload) {
        try {
            EventEnvelope event = objectMapper.readValue(payload, EventEnvelope.class);
            webhookService.processParcelStatusChangedEvent(event);
        } catch (Exception e) {
            log.error("Failed to process parcel.status-changed.v1 event in integration-webhook group: {}", e.getMessage(), e);
        }
    }
}
