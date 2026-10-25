package com.swiftdrop.operations.dispatch;

import com.swiftdrop.common.EventEnvelope;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-Java domain component for maintaining live active delivery workload counts
 * per courier based on Kafka domain events.
 *
 * <p>Implements idempotent event consumption by {@code eventId} and out-of-order event
 * rejection based on parcel {@code sequence} numbers. No Spring or DB dependencies.</p>
 */
public class WorkloadCounter {

    public static final String TOPIC_PARCEL_ASSIGNED = "parcel.assigned.v1";
    public static final String TOPIC_PARCEL_STATUS_CHANGED = "parcel.status-changed.v1";

    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final Map<String, Integer> courierWorkloads = new ConcurrentHashMap<>();
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastParcelSequences = new ConcurrentHashMap<>();

    /**
     * Applies a domain event to the courier workload state.
     *
     * @param event Canonical event envelope
     * @return {@code true} if the event was processed and applied, {@code false} if ignored (duplicate, out-of-order, or invalid)
     */
    public boolean apply(EventEnvelope event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            return false;
        }

        // 1. Idempotency Check: reject duplicate eventId
        if (processedEventIds.contains(event.eventId())) {
            return false;
        }

        // 2. Out-of-Order Check: reject stale events based on parcel sequence
        String parcelId = event.parcelId();
        Long sequence = event.sequence();
        if (parcelId != null && sequence != null) {
            Long lastSeq = lastParcelSequences.get(parcelId);
            if (lastSeq != null && sequence <= lastSeq) {
                // Stale / out-of-order event for this parcel
                return false;
            }
        }

        // 3. Mutate workload counts according to event rules
        String eventType = event.eventType();
        boolean stateMutated = false;

        if (isAssignedEvent(eventType)) {
            // Reassignment: decrement previous courier if specified and different
            if (event.previousCourierId() != null && !event.previousCourierId().isBlank()
                    && !Objects.equals(event.previousCourierId(), event.courierId())) {
                decrement(event.previousCourierId());
            }

            // Increment new courier's count
            if (event.courierId() != null && !event.courierId().isBlank()) {
                increment(event.courierId());
                stateMutated = true;
            }
        } else if (isStatusChangedEvent(eventType)) {
            String toStatus = event.toStatus();
            if (isTerminalStatus(toStatus)) {
                if (event.courierId() != null && !event.courierId().isBlank()) {
                    decrement(event.courierId());
                    stateMutated = true;
                }
            }
        }

        // 4. Record event as processed and record last sequence
        processedEventIds.add(event.eventId());
        if (parcelId != null && sequence != null) {
            lastParcelSequences.put(parcelId, sequence);
        }

        return stateMutated || isAssignedEvent(eventType) || isStatusChangedEvent(eventType);
    }

    /**
     * Retrieves the current active delivery count for a courier.
     *
     * @param courierId Unique courier identifier
     * @return Non-negative active parcel count
     */
    public int getActiveCount(String courierId) {
        if (courierId == null) {
            return 0;
        }
        return courierWorkloads.getOrDefault(courierId, 0);
    }

    /**
     * Checks if a specific event has already been processed.
     *
     * @param eventId Event UUID
     * @return {@code true} if already processed
     */
    public boolean isProcessed(String eventId) {
        if (eventId == null) {
            return false;
        }
        return processedEventIds.contains(eventId);
    }

    /**
     * Returns an unmodifiable snapshot of all courier workload counts.
     */
    public Map<String, Integer> getAllCounts() {
        return Collections.unmodifiableMap(courierWorkloads);
    }

    /**
     * Resets all internal in-memory counters and history.
     */
    public void reset() {
        courierWorkloads.clear();
        processedEventIds.clear();
        lastParcelSequences.clear();
    }

    private void increment(String courierId) {
        courierWorkloads.compute(courierId, (id, count) -> (count == null ? 1 : count + 1));
    }

    private void decrement(String courierId) {
        courierWorkloads.compute(courierId, (id, count) -> {
            if (count == null || count <= 1) {
                return 0;
            }
            return count - 1;
        });
    }

    private boolean isAssignedEvent(String eventType) {
        return TOPIC_PARCEL_ASSIGNED.equalsIgnoreCase(eventType)
                || "PARCEL_ASSIGNED".equalsIgnoreCase(eventType);
    }

    private boolean isStatusChangedEvent(String eventType) {
        return TOPIC_PARCEL_STATUS_CHANGED.equalsIgnoreCase(eventType)
                || "PARCEL_STATUS_CHANGED".equalsIgnoreCase(eventType);
    }

    private boolean isTerminalStatus(String status) {
        return STATUS_DELIVERED.equalsIgnoreCase(status)
                || STATUS_FAILED.equalsIgnoreCase(status)
                || STATUS_CANCELLED.equalsIgnoreCase(status);
    }
}
