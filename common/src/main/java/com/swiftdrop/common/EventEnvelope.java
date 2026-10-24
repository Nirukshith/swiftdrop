package com.swiftdrop.common;

import java.time.Instant;

public record EventEnvelope(
    String eventId,
    String eventType,
    Instant occurredAt,
    Long sequence,
    String parcelId,
    String trackingNumber,
    String fromStatus,
    String toStatus,
    String courierId,
    String previousCourierId,
    String merchantId,
    String customerId,
    String reason
) {}
