package com.swiftdrop.auth.event;

import java.time.Instant;

/**
 * Event published to Kafka topic 'user.registered.v1'.
 * Matches schema in contracts/events/user-registered.schema.json.
 */
public record UserRegisteredEvent(
    String eventId,
    String eventType,
    Instant occurredAt,
    String userId,
    String email,
    String fullName,
    String phoneNumber,
    String role,
    String merchantId,
    boolean enabled
) {}
