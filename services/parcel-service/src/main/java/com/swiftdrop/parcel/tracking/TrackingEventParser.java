package com.swiftdrop.parcel.tracking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads parcel.status-changed.v1 messages (contracts/events/parcel-status-changed.schema.json,
 * common.EventEnvelope) and checks the fields the tracking package relies on.
 */
@Component
class TrackingEventParser {

    static final String STATUS_CHANGED = "PARCEL_STATUS_CHANGED";

    private final ObjectMapper objectMapper;

    TrackingEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @return the status change, or empty for other event types on the topic
     * @throws InvalidTrackingEventException for unreadable JSON or missing required fields
     */
    Optional<StatusChange> parse(String json) {
        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidTrackingEventException("Unreadable status-changed event", e);
        }
        if (envelope == null) {
            throw new InvalidTrackingEventException("Empty status-changed event");
        }
        if (!STATUS_CHANGED.equals(envelope.eventType())) {
            return Optional.empty();
        }
        require(envelope.eventId(), "eventId");
        require(envelope.parcelId(), "parcelId");
        require(envelope.trackingNumber(), "trackingNumber");
        if (envelope.sequence() == null || envelope.sequence() < 1) {
            throw new InvalidTrackingEventException("sequence must be 1 or more in event " + envelope.eventId());
        }
        if (envelope.occurredAt() == null) {
            throw new InvalidTrackingEventException("occurredAt is missing in event " + envelope.eventId());
        }
        return Optional.of(new StatusChange(
                envelope.eventId(),
                envelope.parcelId(),
                envelope.trackingNumber(),
                envelope.sequence(),
                envelope.fromStatus(),
                status(envelope.toStatus(), envelope.eventId()),
                envelope.reason(),
                envelope.occurredAt()));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidTrackingEventException(field + " is missing in status-changed event");
        }
    }

    private static ParcelStatus status(String value, String eventId) {
        try {
            return ParcelStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTrackingEventException("Unknown toStatus " + value + " in event " + eventId, e);
        }
    }
}
