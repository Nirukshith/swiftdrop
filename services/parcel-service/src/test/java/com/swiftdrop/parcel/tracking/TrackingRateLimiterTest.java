package com.swiftdrop.parcel.tracking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingRateLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong clock = new AtomicLong(1_000 * SECOND);

    /** 30 per minute = one token every 2 s; burst 10 refills completely in 20 s. */
    private TrackingRateLimiter limiter(int maxClients) {
        return new TrackingRateLimiter(30, 10, maxClients, clock::get);
    }

    @Test
    @DisplayName("Allows the burst, then rejects with Retry-After")
    void burstThenReject() {
        TrackingRateLimiter limiter = limiter(100);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("a").allowed()).as("request %d", i + 1).isTrue();
        }
        TrackingRateLimiter.Decision rejected = limiter.tryAcquire("a");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(2);
    }

    @Test
    @DisplayName("Tokens come back at the steady rate")
    void refills() {
        TrackingRateLimiter limiter = limiter(100);
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("a");
        }
        clock.addAndGet(SECOND);
        TrackingRateLimiter.Decision halfway = limiter.tryAcquire("a");
        assertThat(halfway.allowed()).isFalse();
        assertThat(halfway.retryAfterSeconds()).isEqualTo(1);

        clock.addAndGet(SECOND);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
    }

    @Test
    @DisplayName("Each client has its own bucket")
    void perClient() {
        TrackingRateLimiter limiter = limiter(100);
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("a");
        }
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    @DisplayName("Idle clients are evicted once their bucket would be full again")
    void evictsIdleClients() {
        TrackingRateLimiter limiter = limiter(100);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        assertThat(limiter.trackedClients()).isEqualTo(2);

        clock.addAndGet(10 * SECOND);
        limiter.tryAcquire("b");
        clock.addAndGet(11 * SECOND); // a idle 21 s, b idle 11 s
        limiter.tryAcquire("c");

        assertThat(limiter.trackedClients()).isEqualTo(2); // a evicted, b and c kept
    }

    @Test
    @DisplayName("When the table is full of active clients, newcomers are limited instead of growing memory")
    void capOnTrackedClients() {
        TrackingRateLimiter limiter = limiter(2);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();

        TrackingRateLimiter.Decision newcomer = limiter.tryAcquire("c");
        assertThat(newcomer.allowed()).isFalse();
        assertThat(newcomer.retryAfterSeconds()).isPositive();
        assertThat(limiter.trackedClients()).isEqualTo(2);

        assertThat(limiter.tryAcquire("a").allowed()).as("known clients keep working").isTrue();

        clock.addAndGet(21 * SECOND);
        assertThat(limiter.tryAcquire("c").allowed()).as("room again after eviction").isTrue();
    }
}
