package com.swiftdrop.integration.webhook;

public record TestWebhookRequest(
        String targetUrlOverride,
        String sampleEventType
) {
}
