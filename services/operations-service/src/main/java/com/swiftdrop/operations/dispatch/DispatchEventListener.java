package com.swiftdrop.operations.dispatch;

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
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Component
public class DispatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventListener.class);

    private final WorkloadCounter workloadCounter;
    private final CourierWorkloadRepository courierWorkloadRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ParcelSequenceRepository parcelSequenceRepository;
    private final ObjectMapper objectMapper;

    public DispatchEventListener(
        WorkloadCounter workloadCounter,
        CourierWorkloadRepository courierWorkloadRepository,
        ProcessedEventRepository processedEventRepository,
        ParcelSequenceRepository parcelSequenceRepository,
        ObjectMapper objectMapper
    ) {
        this.workloadCounter = workloadCounter;
        this.courierWorkloadRepository = courierWorkloadRepository;
        this.processedEventRepository = processedEventRepository;
        this.parcelSequenceRepository = parcelSequenceRepository;
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
            "${swiftdrop.kafka.topics.parcel-assigned:parcel.assigned.v1}",
            "${swiftdrop.kafka.topics.parcel-status-changed:parcel.status-changed.v1}"
        },
        groupId = "operations-dispatch"
    )
    @Transactional
    public void onEvent(String payload) throws JsonProcessingException {
        EventEnvelope event = objectMapper.readValue(payload, EventEnvelope.class);
        processEvent(event);
    }

    @DltHandler
    public void handleDlt(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Exhausted retry attempts for dispatch event from topic {}: {}", topic, payload);
    }

    @Transactional
    public void processEvent(EventEnvelope event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            return;
        }

        // 1. Idempotency check in database
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.debug("Event {} already processed in processed_event table, skipping.", event.eventId());
            return;
        }

        // 2. Persistent per-parcel sequence check (survives application restarts)
        String parcelId = event.parcelId();
        Long sequence = event.sequence();
        if (parcelId != null && sequence != null) {
            Optional<ParcelSequenceEntity> seqOpt = parcelSequenceRepository.findById(parcelId);
            if (seqOpt.isPresent() && sequence <= seqOpt.get().getLastSequence()) {
                log.warn("Stale or out-of-order event for parcel {}: received sequence {} <= lastSequence {}",
                        parcelId, sequence, seqOpt.get().getLastSequence());
                return;
            }
        }

        // 3. Domain state validation via WorkloadCounter
        boolean applied = workloadCounter.apply(event);
        if (!applied) {
            log.debug("WorkloadCounter rejected or ignored event: {}", event.eventId());
            return;
        }

        // 4. Update database courier workload counters
        String eventType = event.eventType();
        if (isAssignedEvent(eventType)) {
            boolean isSameCourier = event.previousCourierId() != null
                    && !event.previousCourierId().isBlank()
                    && Objects.equals(event.previousCourierId(), event.courierId());

            if (!isSameCourier) {
                // Reassignment: decrement previous courier
                if (event.previousCourierId() != null && !event.previousCourierId().isBlank()) {
                    decrementWorkload(event.previousCourierId(), sequence, event.eventId());
                }

                // Increment assigned courier
                if (event.courierId() != null && !event.courierId().isBlank()) {
                    incrementWorkload(event.courierId(), sequence, event.eventId());
                }
            }
        } else if (isStatusChangedEvent(eventType)) {
            String toStatus = event.toStatus();
            if (isTerminalStatus(toStatus) && event.courierId() != null && !event.courierId().isBlank()) {
                decrementWorkload(event.courierId(), sequence, event.eventId());
            }
        }

        // 5. Update per-parcel sequence record atomically
        if (parcelId != null && sequence != null) {
            ParcelSequenceEntity seqEntity = parcelSequenceRepository.findById(parcelId)
                    .orElseGet(() -> new ParcelSequenceEntity(parcelId, sequence));
            seqEntity.setLastSequence(sequence);
            parcelSequenceRepository.save(seqEntity);
        }

        // 6. Record event as processed for persistence idempotency
        processedEventRepository.save(new ProcessedEventEntity(
            event.eventId(),
            event.eventType() != null ? event.eventType() : "UNKNOWN",
            event.parcelId()
        ));
    }

    private void incrementWorkload(String courierId, Long sequence, String eventId) {
        CourierWorkloadEntity entity = courierWorkloadRepository.findByCourierId(courierId)
            .orElseGet(() -> new CourierWorkloadEntity(courierId, 0, sequence != null ? sequence : 0L, eventId));
        entity.setActiveCount(entity.getActiveCount() + 1);
        if (sequence != null) {
            entity.setLastSequence(sequence);
        }
        entity.setLastEventId(eventId);
        courierWorkloadRepository.save(entity);
    }

    private void decrementWorkload(String courierId, Long sequence, String eventId) {
        CourierWorkloadEntity entity = courierWorkloadRepository.findByCourierId(courierId)
            .orElseGet(() -> new CourierWorkloadEntity(courierId, 0, sequence != null ? sequence : 0L, eventId));
        entity.setActiveCount(Math.max(0, entity.getActiveCount() - 1));
        if (sequence != null) {
            entity.setLastSequence(sequence);
        }
        entity.setLastEventId(eventId);
        courierWorkloadRepository.save(entity);
    }

    private boolean isAssignedEvent(String eventType) {
        return WorkloadCounter.TOPIC_PARCEL_ASSIGNED.equalsIgnoreCase(eventType)
            || "PARCEL_ASSIGNED".equalsIgnoreCase(eventType);
    }

    private boolean isStatusChangedEvent(String eventType) {
        return WorkloadCounter.TOPIC_PARCEL_STATUS_CHANGED.equalsIgnoreCase(eventType)
            || "PARCEL_STATUS_CHANGED".equalsIgnoreCase(eventType);
    }

    private boolean isTerminalStatus(String status) {
        return WorkloadCounter.STATUS_DELIVERED.equalsIgnoreCase(status)
            || WorkloadCounter.STATUS_FAILED.equalsIgnoreCase(status)
            || WorkloadCounter.STATUS_CANCELLED.equalsIgnoreCase(status);
    }
}
