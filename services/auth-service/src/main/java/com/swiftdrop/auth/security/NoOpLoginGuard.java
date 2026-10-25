package com.swiftdrop.auth.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default no-op implementation of {@link LoginGuard}.
 *
 * <p>Enables auth-service core login controllers and integration tests (owned by M10)
 * to run immediately without blocking on M7's sliding window cache or database-backed lockout engine.
 *
 * <p>Owner: Member M7 (Security & Lockout)
 */
@Component
@ConditionalOnMissingBean(value = LoginGuard.class, ignored = NoOpLoginGuard.class)
public class NoOpLoginGuard implements LoginGuard {

    @Override
    public void checkLoginAllowed(String email) {
        // No-op: allows all login requests
    }

    @Override
    public void checkLoginAllowed(String email, String ipAddress) {
        // No-op: allows all login requests
    }

    @Override
    public void recordSuccess(String email) {
        // No-op: no counter to reset
    }

    @Override
    public void recordSuccess(String email, String ipAddress) {
        // No-op: no counter to reset
    }

    @Override
    public void recordFailure(String email) {
        // No-op: no counter to increment
    }

    @Override
    public void recordFailure(String email, String ipAddress) {
        // No-op: no counter to increment
    }
}
