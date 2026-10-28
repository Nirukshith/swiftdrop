package com.swiftdrop.integration.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.integration.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "5",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 16000),
            autoCreateTopics = "true",
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".dlt"
    )
    @KafkaListener(
            topics = "parcel.status-changed.v1",
            groupId = "integration-notification"
    )
    public void onParcelStatusChanged(String payload) throws JsonProcessingException {
        log.info("Received parcel.status-changed.v1 event: {}", payload);
        EventEnvelope event = objectMapper.readValue(payload, EventEnvelope.class);
        notificationService.processStatusChangeEvent(event);
    }

    @DltHandler
    public void handleDlt(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Exhausted retry attempts for notification event from topic {}: {}", topic, payload);
    }
}

