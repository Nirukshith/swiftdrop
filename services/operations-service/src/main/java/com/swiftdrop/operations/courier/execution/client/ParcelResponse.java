package com.swiftdrop.operations.courier.execution.client;

import java.time.Instant;
import java.util.UUID;

public record ParcelResponse(
    UUID id,
    String trackingNumber,
    String status,
    String customerId,
    String merchantId,
    String courierId,
    AddressDto pickupAddress,
    AddressDto dropoffAddress,
    Double weightKg,
    String size,
    Double fee,
    Integer attemptCount,
    String failureReason,
    String recipientName,
    String recipientPhone,
    String notes,
    DeliveryProofDto proof,
    Long sequence,
    Instant createdAt,
    Instant updatedAt
) {}
