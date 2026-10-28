package com.swiftdrop.operations.dispatch.dto;

import java.time.Instant;

public record DispatchParcelSummary(
    String parcelId,
    String trackingNumber,
    String status,
    String pickupZone,
    String dropoffZone,
    String pickupAddress,
    String dropoffAddress,
    Double weightKg,
    Integer attemptCount,
    String failureReason,
    Instant createdAt
) {}
