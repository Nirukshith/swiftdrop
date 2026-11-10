package com.swiftdrop.integration.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeliveryPolicyTest {

    @Test
    void retriesOnlyTransientFailures() {
        for (int status : new int[]{0, 429, 500, 502, 503, 504}) {
            assertThat(WebhookDeliveryPolicy.isRetryable(result(status))).isTrue();
        }
        for (int status : new int[]{200, 301, 400, 401, 403, 404, 422}) {
            assertThat(WebhookDeliveryPolicy.isRetryable(result(status))).isFalse();
        }
    }

    @Test
    void fiveTotalAttemptsHaveFourExponentialWaits() {
        assertThat(WebhookDeliveryPolicy.MAX_ATTEMPTS).isEqualTo(5);
        assertThat(WebhookDeliveryPolicy.nextDelay(1)).contains(Duration.ofSeconds(1));
        assertThat(WebhookDeliveryPolicy.nextDelay(2)).contains(Duration.ofSeconds(2));
        assertThat(WebhookDeliveryPolicy.nextDelay(3)).contains(Duration.ofSeconds(4));
        assertThat(WebhookDeliveryPolicy.nextDelay(4)).contains(Duration.ofSeconds(8));
        assertThat(WebhookDeliveryPolicy.nextDelay(5)).isEmpty();
    }

    private static WebhookDispatcher.DispatchResult result(int status) {
        return new WebhookDispatcher.DispatchResult(
                status >= 200 && status < 300, status, 1, Map.of(), Map.of(),
                null, null, null, null, null
        );
    }
}
