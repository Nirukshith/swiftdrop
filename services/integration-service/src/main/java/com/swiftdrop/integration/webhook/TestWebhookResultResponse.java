package com.swiftdrop.integration.webhook;

import java.time.Instant;

public record TestWebhookResultResponse(
        boolean success,
        int statusCode,
        int latencyMs,
        Instant attemptedAt,
        String targetUrl,
        String signatureSent,
        String timestampSent,
        String responseBodySummary,
        String errorMessage
) {
}
