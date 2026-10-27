package com.swiftdrop.integration.webhook;

import java.time.Instant;

public record DeliveryAttemptRecord(
        int attemptNumber,
        Instant attemptedAt,
        Integer httpStatusCode,
        int latencyMs,
        boolean success,
        String errorMessage
) {
}
