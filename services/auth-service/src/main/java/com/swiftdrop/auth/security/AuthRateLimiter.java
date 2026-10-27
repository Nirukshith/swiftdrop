package com.swiftdrop.auth.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window rate limiter for login and registration requests.
 */
@Component
public class AuthRateLimiter {

    public static final int DEFAULT_LOGIN_LIMIT = 10;
    public static final int DEFAULT_REGISTER_LIMIT = 5;
    public static final long WINDOW_SECONDS = 60;

    private final ConcurrentHashMap<String, Deque<Long>> requestLogs = new ConcurrentHashMap<>();

    private int loginLimit = DEFAULT_LOGIN_LIMIT;
    private int registerLimit = DEFAULT_REGISTER_LIMIT;

    public synchronized boolean allowLogin(String clientKey) {
        return allowRequest("login:" + clientKey, loginLimit);
    }

    public synchronized boolean allowRegister(String clientKey) {
        return allowRequest("register:" + clientKey, registerLimit);
    }

    private boolean allowRequest(String key, int maxRequests) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - WINDOW_SECONDS;

        Deque<Long> timestamps = requestLogs.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Evict expired timestamps
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    public void setLoginLimit(int limit) {
        this.loginLimit = limit;
    }

    public void setRegisterLimit(int limit) {
        this.registerLimit = limit;
    }

    public void reset() {
        requestLogs.clear();
    }
}
