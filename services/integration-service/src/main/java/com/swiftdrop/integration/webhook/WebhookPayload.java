package com.swiftdrop.integration.webhook;

import java.time.Instant;
import java.util.UUID;

public record WebhookPayload(
        UUID deliveryId,
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long sequence,
        UUID merchantId,
        WebhookParcelFields parcel
) {
}
