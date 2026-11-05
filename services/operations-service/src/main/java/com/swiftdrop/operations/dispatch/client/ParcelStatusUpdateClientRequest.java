package com.swiftdrop.operations.dispatch.client;

public record ParcelStatusUpdateClientRequest(
    String status,
    String expectedCurrentStatus,
    String courierId,
    String reason,
    String notes,
    String signatureUrl
) {}
