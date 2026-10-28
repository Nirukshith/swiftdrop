package com.swiftdrop.operations.courier.execution.client;

public record ParcelStatusUpdateRequest(
    String status,
    String expectedCurrentStatus,
    String courierId,
    String reason,
    DeliveryProofDto proof,
    String idempotencyKey
) {}
