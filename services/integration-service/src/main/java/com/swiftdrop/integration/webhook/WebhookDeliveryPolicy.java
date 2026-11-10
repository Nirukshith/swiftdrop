package com.swiftdrop.integration.webhook;

import java.time.Duration;
import java.util.Optional;

/** Delivery policy for webhook HTTP attempts; scheduling is owned by Kafka retry topics. */
final class WebhookDeliveryPolicy {

    static final int MAX_ATTEMPTS = 5;
    private static final long INITIAL_DELAY_MS = 1_000;
    private static final long MAX_DELAY_MS = 16_000;

    private WebhookDeliveryPolicy() {
    }

    static boolean isRetryable(WebhookDispatcher.DispatchResult result) {
        int status = result.statusCode();
        return !result.success() && (status == 0 || status == 429 || status >= 500);
    }

    static Optional<Duration> nextDelay(int completedAttempts) {
        if (completedAttempts < 1 || completedAttempts >= MAX_ATTEMPTS) {
            return Optional.empty();
        }
        long delay = Math.min(INITIAL_DELAY_MS << (completedAttempts - 1), MAX_DELAY_MS);
        return Optional.of(Duration.ofMillis(delay));
    }
}
