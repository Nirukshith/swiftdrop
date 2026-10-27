package com.swiftdrop.integration.webhook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookSettingsResponse(
        UUID merchantId,
        String url,
        boolean active,
        List<String> subscribedEvents,
        boolean secretConfigured,
        String secretPreview,
        String rawSecret,
        Instant createdAt,
        Instant updatedAt
) {
}
