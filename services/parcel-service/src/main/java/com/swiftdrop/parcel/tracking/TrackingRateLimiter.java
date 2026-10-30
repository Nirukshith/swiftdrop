package com.swiftdrop.parcel.tracking;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * In-memory token bucket per client key (IP). Each bucket holds up to {@code burst} tokens and
 * refills at {@code requestsPerMinute}. A bucket that has been idle long enough to refill
 * completely is the same as a new one, so it is evicted; this keeps memory bounded by the
 * number of recently active clients, with a hard cap of {@code maxTrackedClients}.
 */
final class TrackingRateLimiter {

    record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOW = new Decision(true, 0);
    }

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int capacity;
    private final double nanosPerToken;
    private final long fullRefillNanos;
    private final int maxTrackedClients;
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep;

    TrackingRateLimiter(int requestsPerMinute, int burst, int maxTrackedClients, LongSupplier nanoClock) {
        if (requestsPerMinute < 1 || burst < 1 || maxTrackedClients < 1) {
            throw new IllegalArgumentException("rate limit settings must be positive");
        }
        this.capacity = burst;
        this.nanosPerToken = 60.0 * NANOS_PER_SECOND / requestsPerMinute;
        this.fullRefillNanos = (long) Math.ceil(capacity * nanosPerToken);
        this.maxTrackedClients = maxTrackedClients;
        this.nanoClock = nanoClock;
        this.lastSweep = new AtomicLong(nanoClock.getAsLong());
    }

    Decision tryAcquire(String clientKey) {
        long now = nanoClock.getAsLong();
        sweepIfDue(now);

        Bucket bucket = buckets.get(clientKey);
        if (bucket == null) {
            if (buckets.size() >= maxTrackedClients) {
                evictIdle(now);
                if (buckets.size() >= maxTrackedClients) {
                    // Table full of active clients: limit newcomers until old entries expire.
                    return new Decision(false, secondsUntilOneToken(0));
                }
            }
            bucket = buckets.computeIfAbsent(clientKey, k -> new Bucket(capacity, now));
        }
        return bucket.take(now);
    }

    int trackedClients() {
        return buckets.size();
    }

    private void sweepIfDue(long now) {
        long last = lastSweep.get();
        if (now - last >= fullRefillNanos && lastSweep.compareAndSet(last, now)) {
            evictIdle(now);
        }
    }

    private void evictIdle(long now) {
        buckets.values().removeIf(b -> b.isIdle(now));
    }

    private long secondsUntilOneToken(double tokens) {
        double nanos = (1.0 - tokens) * nanosPerToken;
        return Math.max(1, (long) Math.ceil(nanos / NANOS_PER_SECOND));
    }

    private final class Bucket {
        private double tokens;
        private long updatedAt;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.updatedAt = now;
        }

        synchronized Decision take(long now) {
            refill(now);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return Decision.ALLOW;
            }
            return new Decision(false, secondsUntilOneToken(tokens));
        }

        synchronized boolean isIdle(long now) {
            return now - updatedAt >= fullRefillNanos;
        }

        private void refill(long now) {
            long elapsed = now - updatedAt;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed / nanosPerToken);
                updatedAt = now;
            }
        }
    }
}
