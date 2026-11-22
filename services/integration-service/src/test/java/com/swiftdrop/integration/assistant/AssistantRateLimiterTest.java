package com.swiftdrop.integration.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.integration.assistant.service.AssistantRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssistantRateLimiterTest {

    /** Clock that tests can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-22T10:00:00Z"));
    private final AssistantRateLimiter limiter = new AssistantRateLimiter(3, Duration.ofMinutes(1), clock);

    @Test
    @DisplayName("Allows up to the limit, then rejects within the same window")
    void rejectsOverLimit() {
        assertThat(limiter.tryAcquire("cust-1")).isTrue();
        assertThat(limiter.tryAcquire("cust-1")).isTrue();
        assertThat(limiter.tryAcquire("cust-1")).isTrue();
        assertThat(limiter.tryAcquire("cust-1")).isFalse();
    }

    @Test
    @DisplayName("Limits are tracked separately per user")
    void tracksUsersIndependently() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("cust-1");
        }
        assertThat(limiter.tryAcquire("cust-1")).isFalse();
        assertThat(limiter.tryAcquire("cust-2")).isTrue();
    }

    @Test
    @DisplayName("Requests become available again as old ones leave the sliding window")
    void slidingWindowFreesCapacity() {
        limiter.tryAcquire("cust-1");
        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire("cust-1");
        limiter.tryAcquire("cust-1");
        assertThat(limiter.tryAcquire("cust-1")).isFalse();

        clock.advance(Duration.ofSeconds(31)); // first request is now older than one minute
        assertThat(limiter.tryAcquire("cust-1")).isTrue();
        assertThat(limiter.tryAcquire("cust-1")).isFalse();
    }

    @Test
    @DisplayName("Rejected requests do not extend the user's lockout")
    void rejectedRequestsAreNotCounted() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("cust-1");
        }
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("cust-1")).isFalse();
        }
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertThat(limiter.tryAcquire("cust-1")).isTrue();
    }

    @Test
    @DisplayName("Invalid configuration is rejected")
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new AssistantRateLimiter(0, Duration.ofMinutes(1), clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssistantRateLimiter(1, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
