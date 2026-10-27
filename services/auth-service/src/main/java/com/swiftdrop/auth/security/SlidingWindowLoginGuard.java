package com.swiftdrop.auth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production implementation of {@link LoginGuard} utilizing sliding time windows,
 * progressive tarpitting delays, and multi-key bucket isolation (per-account, per-source IP,
 * and composite email+IP) to defend against brute-force attacks and victim denial-of-service.
 *
 * <p>Activated when {@code swiftdrop.security.lockout.enabled=true}.
 *
 * <p>Owner: Member M7 (Security & Lockout)
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.security.lockout.enabled", havingValue = "true")
public class SlidingWindowLoginGuard implements LoginGuard {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowLoginGuard.class);

    private final long windowSeconds;
    private final int maxAccountAttempts;
    private final int maxSourceAttempts;
    private final long lockoutDurationSeconds;
    private final int tarpitThreshold;
    private final boolean tarpitEnabled;

    private final Clock clock;

    // Per-account attempt history: normalized email -> deque of failure epoch seconds
    private final Map<String, Deque<Long>> accountAttempts = new ConcurrentHashMap<>();

    // Per-source attempt history: IP -> deque of failure epoch seconds
    private final Map<String, Deque<Long>> sourceAttempts = new ConcurrentHashMap<>();

    // Composite attempt history: email#IP -> deque of failure epoch seconds
    private final Map<String, Deque<Long>> compositeAttempts = new ConcurrentHashMap<>();

    // Lockout expiry tracker: key -> lock expiry epoch seconds
    private final Map<String, Long> lockouts = new ConcurrentHashMap<>();

    public SlidingWindowLoginGuard() {
        this(900L, 5, 20, 900L, 3, true, Clock.systemUTC());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SlidingWindowLoginGuard(
            @Value("${swiftdrop.security.lockout.window-seconds:900}") long windowSeconds,
            @Value("${swiftdrop.security.lockout.max-account-attempts:5}") int maxAccountAttempts,
            @Value("${swiftdrop.security.lockout.max-source-attempts:20}") int maxSourceAttempts,
            @Value("${swiftdrop.security.lockout.duration-seconds:900}") long lockoutDurationSeconds,
            @Value("${swiftdrop.security.lockout.tarpit-threshold:3}") int tarpitThreshold,
            @Value("${swiftdrop.security.lockout.tarpit-enabled:true}") boolean tarpitEnabled) {
        this(windowSeconds, maxAccountAttempts, maxSourceAttempts, lockoutDurationSeconds,
                tarpitThreshold, tarpitEnabled, Clock.systemUTC());
    }

    public SlidingWindowLoginGuard(
            long windowSeconds,
            int maxAccountAttempts,
            int maxSourceAttempts,
            long lockoutDurationSeconds,
            int tarpitThreshold,
            boolean tarpitEnabled,
            Clock clock) {
        this.windowSeconds = windowSeconds;
        this.maxAccountAttempts = maxAccountAttempts;
        this.maxSourceAttempts = maxSourceAttempts;
        this.lockoutDurationSeconds = lockoutDurationSeconds;
        this.tarpitThreshold = tarpitThreshold;
        this.tarpitEnabled = tarpitEnabled;
        this.clock = clock;
    }

    @Override
    public void checkLoginAllowed(String email) {
        checkLoginAllowed(email, null);
    }

    @Override
    public void checkLoginAllowed(String email, String ipAddress) {
        long now = nowSeconds();

        // 1. Check Source IP lockout
        if (ipAddress != null && !ipAddress.isBlank()) {
            String ipKey = normalizeIp(ipAddress);
            Long ipLockExpiry = lockouts.get(ipKey);
            if (ipLockExpiry != null) {
                if (now < ipLockExpiry) {
                    log.warn("Login attempt blocked for locked IP source: {}", ipKey);
                    throw new AccountLockedException("IP address " + ipKey + " is temporarily locked until epoch " + ipLockExpiry + " due to excessive failed attempts.");
                } else {
                    lockouts.remove(ipKey);
                }
            }
        }

        // 2. Check Account lockout
        if (email != null && !email.isBlank()) {
            String emailKey = normalizeEmail(email);

            // Check if composite (email, IP) or account is locked
            if (ipAddress != null && !ipAddress.isBlank()) {
                String compKey = compositeKey(emailKey, normalizeIp(ipAddress));
                Long compExpiry = lockouts.get(compKey);
                if (compExpiry != null) {
                    if (now < compExpiry) {
                        log.warn("Login attempt blocked for locked composite target: {}", compKey);
                        throw new AccountLockedException(email, compExpiry);
                    } else {
                        lockouts.remove(compKey);
                    }
                }
            }

            Long accountExpiry = lockouts.get(emailKey);
            if (accountExpiry != null) {
                if (now < accountExpiry) {
                    log.warn("Login attempt blocked for locked account: {}", emailKey);
                    throw new AccountLockedException(email, accountExpiry);
                } else {
                    lockouts.remove(emailKey);
                }
            }

            // 3. Progressive tarpitting delay for repeated failures before hard lockout
            int failures = getRecentFailureCount(accountAttempts.get(emailKey), now);
            if (tarpitEnabled && failures >= tarpitThreshold) {
                applyProgressiveDelay(failures);
            }
        }
    }

    @Override
    public void recordSuccess(String email) {
        recordSuccess(email, null);
    }

    @Override
    public void recordSuccess(String email, String ipAddress) {
        if (email != null && !email.isBlank()) {
            String emailKey = normalizeEmail(email);
            accountAttempts.remove(emailKey);
            lockouts.remove(emailKey);

            if (ipAddress != null && !ipAddress.isBlank()) {
                String compKey = compositeKey(emailKey, normalizeIp(ipAddress));
                compositeAttempts.remove(compKey);
                lockouts.remove(compKey);
            }
            log.debug("Cleared failure history on successful login for {}", emailKey);
        }
    }

    @Override
    public void recordFailure(String email) {
        recordFailure(email, null);
    }

    @Override
    public void recordFailure(String email, String ipAddress) {
        long now = nowSeconds();

        // 1. Record per-account failure
        if (email != null && !email.isBlank()) {
            String emailKey = normalizeEmail(email);
            int accountFails = recordAttempt(accountAttempts, emailKey, now);

            if (accountFails >= maxAccountAttempts) {
                long lockExpiry = now + lockoutDurationSeconds;
                lockouts.put(emailKey, lockExpiry);
                log.warn("Account {} locked until epoch {} ({} failed attempts)", emailKey, lockExpiry, accountFails);
            }

            // Record composite (email, IP)
            if (ipAddress != null && !ipAddress.isBlank()) {
                String compKey = compositeKey(emailKey, normalizeIp(ipAddress));
                int compFails = recordAttempt(compositeAttempts, compKey, now);
                if (compFails >= maxAccountAttempts) {
                    long lockExpiry = now + lockoutDurationSeconds;
                    lockouts.put(compKey, lockExpiry);
                }
            }
        }

        // 2. Record per-source failure
        if (ipAddress != null && !ipAddress.isBlank()) {
            String ipKey = normalizeIp(ipAddress);
            int sourceFails = recordAttempt(sourceAttempts, ipKey, now);

            if (sourceFails >= maxSourceAttempts) {
                long lockExpiry = now + lockoutDurationSeconds;
                lockouts.put(ipKey, lockExpiry);
                log.warn("IP source {} locked until epoch {} ({} failed attempts across accounts)", ipKey, lockExpiry, sourceFails);
            }
        }
    }

    private int recordAttempt(Map<String, Deque<Long>> map, String key, long now) {
        Deque<Long> deque = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(now);
            cleanExpired(deque, now);
            return deque.size();
        }
    }

    private int getRecentFailureCount(Deque<Long> deque, long now) {
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            cleanExpired(deque, now);
            return deque.size();
        }
    }

    private void cleanExpired(Deque<Long> deque, long now) {
        long cutoff = now - windowSeconds;
        while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
            deque.pollFirst();
        }
    }

    private void applyProgressiveDelay(int failures) {
        // Delay: 4th failure -> 1000ms, 5th failure -> 2000ms (max 4000ms)
        int exponent = failures - tarpitThreshold;
        long delayMs = Math.min(4000L, 1000L * (1L << Math.max(0, exponent - 1)));
        try {
            log.debug("Applying progressive tarpit delay of {} ms (failure count: {})", delayMs, failures);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long nowSeconds() {
        return clock.instant().getEpochSecond();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String ip) {
        return ip.trim();
    }

    private String compositeKey(String email, String ip) {
        return email + "#" + ip;
    }

    // Helper inspection methods for testing and monitoring
    public int getAccountFailureCount(String email) {
        return getRecentFailureCount(accountAttempts.get(normalizeEmail(email)), nowSeconds());
    }

    public int getSourceFailureCount(String ipAddress) {
        return getRecentFailureCount(sourceAttempts.get(normalizeIp(ipAddress)), nowSeconds());
    }

    public boolean isAccountLocked(String email) {
        Long expiry = lockouts.get(normalizeEmail(email));
        return expiry != null && nowSeconds() < expiry;
    }

    public boolean isSourceLocked(String ipAddress) {
        Long expiry = lockouts.get(normalizeIp(ipAddress));
        return expiry != null && nowSeconds() < expiry;
    }

    public void resetAll() {
        accountAttempts.clear();
        sourceAttempts.clear();
        compositeAttempts.clear();
        lockouts.clear();
    }
}
