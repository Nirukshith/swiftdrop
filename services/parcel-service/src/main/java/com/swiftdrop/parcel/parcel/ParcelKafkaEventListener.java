package com.swiftdrop.parcel.parcel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ParcelKafkaEventListener {

    private static final Logger log = LoggerFactory.getLogger(ParcelKafkaEventListener.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ParcelKafkaEventListener(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleParcelEvent(ParcelDomainEvent event) {
        if (kafkaTemplate != null) {
            try {
                String payload = objectMapper.writeValueAsString(event.envelope());
                kafkaTemplate.send(event.topic(), event.parcelId(), payload);
                log.info("Published {} event to topic {} for parcelId={} sequence={}",
                        event.envelope().eventType(), event.topic(), event.parcelId(), event.envelope().sequence());
            } catch (Exception e) {
                log.error("Failed to publish event to topic {}: {}", event.topic(), e.getMessage(), e);
            }
        } else {
            log.info("KafkaTemplate not configured; skipped publishing to topic {} for parcelId={}",
                    event.topic(), event.parcelId());
        }
    }
}
