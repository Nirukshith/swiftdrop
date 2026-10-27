package com.swiftdrop.integration.webhook;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryLogItem(
        UUID deliveryId,
        UUID eventId,
        String eventType,
        String trackingNumber,
        String targetUrl,
        String status,
        Integer httpStatusCode,
        int attempts,
        Instant lastAttemptAt,
        Instant nextRetryAt,
        String errorSummary,
        Instant createdAt
) {
}
