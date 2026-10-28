package com.swiftdrop.operations.dispatch.client;

import java.time.Instant;

public record ParcelClientResponse(
    String id,
    String trackingNumber,
    String status,
    String customerId,
    String merchantId,
    String courierId,
    AddressClientDto pickupAddress,
    AddressClientDto dropoffAddress,
    Double weightKg,
    String size,
    Double fee,
    int attemptCount,
    String failureReason,
    String recipientName,
    String recipientPhone,
    String notes,
    Long sequence,
    Instant createdAt,
    Instant updatedAt
) {}
