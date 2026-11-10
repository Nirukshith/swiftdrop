package com.swiftdrop.integration.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class WebhookKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookKafkaConsumer.class);
    static final String MAIN_TOPIC = "parcel.status-changed.v1";
    static final String RETRY_TOPIC_PREFIX = MAIN_TOPIC + ".webhook.retry-";

    static final String EXCEPTION_HEADER = "x-exception-message";
    static final String DELIVERY_ID_HEADER = "x-delivery-id";
    static final String MERCHANT_ID_HEADER = "x-merchant-id";
    static final String ORIGINAL_TOPIC_HEADER = "x-original-topic";

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public WebhookKafkaConsumer(WebhookService webhookService, ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "5",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 16000),
            autoCreateTopics = "true",
            retryTopicSuffix = ".webhook.retry",
            dltTopicSuffix = ".webhook.dlt",
            include = RetryableWebhookFailure.class,
            traversingCauses = "true"
    )
    @KafkaListener(
            topics = "${swiftdrop.kafka.topics.parcel-status-changed:parcel.status-changed.v1}",
            groupId = "integration-webhook"
    )
    public void onParcelStatusChanged(ConsumerRecord<String, String> record) {
        EventEnvelope event;
        try {
            event = objectMapper.readValue(record.value(), EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new TerminalWebhookFailure("Malformed webhook event envelope");
        }

        WebhookService.DeliveryOutcome outcome = webhookService.processParcelStatusChangedEvent(
                event, record.topic().startsWith(RETRY_TOPIC_PREFIX));
        if (outcome.disposition() == WebhookService.DeliveryDisposition.RETRYABLE_FAILURE
                || outcome.disposition() == WebhookService.DeliveryDisposition.TERMINAL_FAILURE) {
            addFailureHeaders(record, outcome);
            if (outcome.disposition() == WebhookService.DeliveryDisposition.RETRYABLE_FAILURE) {
                throw new RetryableWebhookFailure(outcome.errorMessage());
            }
            throw new TerminalWebhookFailure(outcome.errorMessage());
        }
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        try {
            webhookService.markDltFailed(objectMapper.readValue(record.value(), EventEnvelope.class));
        } catch (JsonProcessingException e) {
            log.warn("Malformed event envelope on webhook DLT");
        }
    }

    private void addFailureHeaders(ConsumerRecord<String, String> record, WebhookService.DeliveryOutcome outcome) {
        putHeader(record, EXCEPTION_HEADER, outcome.errorMessage());
        putHeader(record, DELIVERY_ID_HEADER, outcome.deliveryId().toString());
        putHeader(record, MERCHANT_ID_HEADER, outcome.merchantId().toString());
        putHeader(record, ORIGINAL_TOPIC_HEADER, MAIN_TOPIC);
    }

    private void putHeader(ConsumerRecord<String, String> record, String name, String value) {
        record.headers().remove(name);
        record.headers().add(name, (value != null ? value : "Webhook delivery failed")
                .getBytes(StandardCharsets.UTF_8));
    }

    static final class RetryableWebhookFailure extends RuntimeException {
        RetryableWebhookFailure(String message) {
            super(message);
        }
    }

    static final class TerminalWebhookFailure extends RuntimeException {
        TerminalWebhookFailure(String message) {
            super(message);
        }
    }
}
