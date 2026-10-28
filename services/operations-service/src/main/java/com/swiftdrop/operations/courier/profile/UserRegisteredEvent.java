package com.swiftdrop.operations.courier.profile;

import java.time.Instant;

record UserRegisteredEvent(
    String eventId,
    String eventType,
    Instant occurredAt,
    String userId,
    String email,
    String fullName,
    String phoneNumber,
    String role,
    String merchantId,
    Boolean enabled
) {}
