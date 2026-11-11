package com.swiftdrop.integration.notification.dto;

import java.time.Instant;

/**
 * Payload published by auth-service on user.registered.v1.
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
