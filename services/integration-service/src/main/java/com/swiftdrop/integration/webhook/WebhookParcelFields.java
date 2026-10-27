package com.swiftdrop.integration.webhook;

import java.time.Instant;

public record WebhookParcelFields(
        String parcelId,
        String trackingNumber,
        String status,
        String fromStatus,
        String toStatus,
        Double weightKg,
        String recipientName,
        String recipientAddress,
        Instant updatedAt,
        String reason
) {
}
