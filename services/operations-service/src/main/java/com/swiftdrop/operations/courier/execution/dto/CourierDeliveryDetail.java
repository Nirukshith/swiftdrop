package com.swiftdrop.operations.courier.execution.dto;

import java.util.List;
import java.util.UUID;

public record CourierDeliveryDetail(
    UUID parcelId,
    String trackingNumber,
    String status,
    String recipientName,
    String recipientPhone,
    String senderName,
    String senderPhone,
    String pickupAddress,
    String dropoffAddress,
    String googleMapsUrl,
    String specialInstructions,
    Double weightKg,
    Integer attemptCount,
    List<String> allowedNextStatuses,
    Boolean proofRequired,
    DeliveryProofInfo proof,
    String failureReason
) {}
