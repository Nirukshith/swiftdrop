package com.swiftdrop.integration.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.integration.notification.dto.UserRegisteredEvent;
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
            topics = {"parcel.created.v1", "parcel.assigned.v1", "parcel.status-changed.v1"},
            groupId = "integration-notification"
    )
    public void onParcelEvent(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic)
            throws JsonProcessingException {
        // Never log the raw payload: events carry contact details (PII). Log identifiers only.
        EventEnvelope event = objectMapper.readValue(payload, EventEnvelope.class);
        log.info("Received notification event {} from topic {}", event.eventId(), topic);
        if ("parcel.status-changed.v1".equals(topic)) {
            notificationService.processStatusChangeEvent(event);
        } else {
            notificationService.processParcelEvent(event);
        }
    }

    @RetryableTopic(
            attempts = "5",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 16000),
            autoCreateTopics = "true",
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".dlt"
    )
    @KafkaListener(
            topics = "user.registered.v1",
            groupId = "integration-notification"
    )
    public void onUserRegistered(String payload) throws JsonProcessingException {
        UserRegisteredEvent event = objectMapper.readValue(payload, UserRegisteredEvent.class);
        log.info("Received user.registered.v1 event {}", event.eventId());
        notificationService.processUserRegisteredEvent(event);
    }

    @DltHandler
    public void handleDlt(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Exhausted retry attempts for notification event {} from topic {}", eventIdOf(payload), topic);
    }

    private String eventIdOf(String payload) {
        try {
            String eventId = objectMapper.readTree(payload).path("eventId").asText(null);
            return eventId != null && !eventId.isBlank() ? eventId : "<missing eventId>";
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return "<unparseable payload>";
        }
    }
}
