package com.swiftdrop.operations.reports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
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
public class ReportEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReportEventListener.class);

    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    public ReportEventListener(ReportService reportService, ObjectMapper objectMapper) {
        this.reportService = reportService;
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
            topics = {
                    "${swiftdrop.kafka.topics.parcel-created:parcel.created.v1}",
                    "${swiftdrop.kafka.topics.parcel-status-changed:parcel.status-changed.v1}"
            },
            groupId = "operations-reports"
    )
    public void onEvent(String payload) throws JsonProcessingException {
        reportService.processEvent(objectMapper.readValue(payload, EventEnvelope.class));
    }

    @DltHandler
    public void handleDlt(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Exhausted retry attempts for report event from topic {}", topic);
    }
}
