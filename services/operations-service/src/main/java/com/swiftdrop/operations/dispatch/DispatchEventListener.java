package com.swiftdrop.operations.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
public class DispatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventListener.class);

    private final WorkloadCounter workloadCounter;
    private final CourierWorkloadRepository courierWorkloadRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public DispatchEventListener(
        WorkloadCounter workloadCounter,
        CourierWorkloadRepository courierWorkloadRepository,
        ProcessedEventRepository processedEventRepository,
        ObjectMapper objectMapper
    ) {
        this.workloadCounter = workloadCounter;
        this.courierWorkloadRepository = courierWorkloadRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = {
            "${swiftdrop.kafka.topics.parcel-assigned:parcel.assigned.v1}",
            "${swiftdrop.kafka.topics.parcel-status-changed:parcel.status-changed.v1}"
        },
        groupId = "operations-dispatch"
    )
    @Transactional
    public void onEvent(String payload) {
        try {
            EventEnvelope event = objectMapper.readValue(payload, EventEnvelope.class);
            processEvent(event);
        } catch (Exception e) {
            log.error("Failed to parse and process event in operations-dispatch: {}", e.getMessage(), e);
        }
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

        // 2. Pure domain state update
        boolean applied = workloadCounter.apply(event);
        if (!applied) {
            log.debug("WorkloadCounter rejected or ignored event: {}", event.eventId());
        }

        // 3. Update database state
        String eventType = event.eventType();
        if (isAssignedEvent(eventType)) {
            // Reassignment: decrement previous courier
            if (event.previousCourierId() != null && !event.previousCourierId().isBlank()
                    && !Objects.equals(event.previousCourierId(), event.courierId())) {
                decrementWorkload(event.previousCourierId(), event.sequence(), event.eventId());
            }

            // Increment assigned courier
            if (event.courierId() != null && !event.courierId().isBlank()) {
                incrementWorkload(event.courierId(), event.sequence(), event.eventId());
            }
        } else if (isStatusChangedEvent(eventType)) {
            String toStatus = event.toStatus();
            if (isTerminalStatus(toStatus) && event.courierId() != null && !event.courierId().isBlank()) {
                decrementWorkload(event.courierId(), event.sequence(), event.eventId());
            }
        }

        // 4. Record event as processed for persistence idempotency
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
