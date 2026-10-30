package com.swiftdrop.operations.courier.execution.dto;

import java.util.UUID;

public record CourierDeliveryCard(
    UUID parcelId,
    String trackingNumber,
    String status,
    String recipientName,
    String recipientPhone,
    String pickupAddress,
    String dropoffAddress,
    String googleMapsUrl,
    Double weightKg,
    Integer attemptCount,
    Integer sequenceOrder
) {}
