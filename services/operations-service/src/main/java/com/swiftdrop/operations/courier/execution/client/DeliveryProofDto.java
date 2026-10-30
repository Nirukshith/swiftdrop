package com.swiftdrop.operations.courier.execution.client;

public record DeliveryProofDto(
    String recipientName,
    String proofPhotoPath,
    String notes
) {}
