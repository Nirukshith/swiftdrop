package com.swiftdrop.integration.webhook;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WebhookDeliveryDetailResponse(
        UUID deliveryId,
        UUID eventId,
        String eventType,
        String status,
        String targetUrl,
        int attempts,
        Integer httpStatusCode,
        Map<String, String> requestHeaders,
        WebhookPayload payload,
        Map<String, String> responseHeaders,
        String responseBody,
        Instant lastAttemptAt,
        Instant nextRetryAt,
        List<DeliveryAttemptRecord> retryHistory,
        Instant createdAt
) {
}
