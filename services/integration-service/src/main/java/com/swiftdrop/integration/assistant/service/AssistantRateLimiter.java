package com.swiftdrop.integration.assistant.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-user sliding-window rate limit for the assistant chat endpoint.
 *
 * <p>Each customer (JWT subject) may send at most {@code maxRequests} messages per {@code window}.
 * Every message can trigger parcel-service calls and a paid LLM request, so the limit protects both
 * upstream services and the provider quota. State is in-memory and per instance.
 */
@Component
public class AssistantRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> requestsByUser = new ConcurrentHashMap<>();

    @Autowired
    public AssistantRateLimiter(
            @Value("${swiftdrop.assistant.rate-limit.max-requests:10}") int maxRequests,
            @Value("${swiftdrop.assistant.rate-limit.window:PT1M}") Duration window) {
        this(maxRequests, window, Clock.systemUTC());
    }

    public AssistantRateLimiter(int maxRequests, Duration window, Clock clock) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be at least 1");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Records a request for the user if they are under the limit.
     *
     * @return true if the request is allowed, false if the user has exhausted the current window
     */
    public boolean tryAcquire(String userId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);
        boolean[] allowed = {false};
        // compute() runs atomically per key, so the deque is never accessed concurrently.
        requestsByUser.compute(userId, (key, timestamps) -> {
            Deque<Instant> recent = timestamps != null ? timestamps : new ArrayDeque<>();
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(windowStart)) {
                recent.pollFirst();
            }
            if (recent.size() < maxRequests) {
                recent.addLast(now);
                allowed[0] = true;
            }
            return recent;
        });
        return allowed[0];
    }
}
