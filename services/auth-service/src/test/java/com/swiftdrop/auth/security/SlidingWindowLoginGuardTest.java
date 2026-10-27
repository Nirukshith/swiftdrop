package com.swiftdrop.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingWindowLoginGuardTest {

    private MutableClock clock;
    private SlidingWindowLoginGuard guard;

    private static class MutableClock extends Clock {
        private Instant currentInstant;
        private final ZoneOffset zone = ZoneOffset.UTC;

        MutableClock(Instant start) {
            this.currentInstant = start;
        }

        void advanceSeconds(long seconds) {
            this.currentInstant = currentInstant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return zone;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.ofEpochSecond(1729864800L));
        // window = 900s, maxAccount = 5, maxSource = 20, lockDuration = 900s, tarpitThreshold = 3, tarpitEnabled = false (for fast unit testing)
        guard = new SlidingWindowLoginGuard(900L, 5, 20, 900L, 3, false, clock);
    }

    @Test
    @DisplayName("Under 5 failed attempts allows login without throwing")
    void underThreshold_allowsLogin() {
        String email = "alice@swiftdrop.com";

        for (int i = 0; i < 4; i++) {
            guard.recordFailure(email);
        }

        assertThat(guard.getAccountFailureCount(email)).isEqualTo(4);
        assertThat(guard.isAccountLocked(email)).isFalse();
        assertThatCode(() -> guard.checkLoginAllowed(email)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("5 consecutive failures locks account and checkLoginAllowed throws AccountLockedException")
    void accountThresholdReached_locksAccount() {
        String email = "bob@swiftdrop.com";

        for (int i = 0; i < 5; i++) {
            guard.recordFailure(email);
        }

        assertThat(guard.isAccountLocked(email)).isTrue();
        assertThatThrownBy(() -> guard.checkLoginAllowed(email))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining(email);
    }

    @Test
    @DisplayName("Lockout expires after 15 minutes window")
    void lockoutExpires_afterDuration() {
        String email = "charlie@swiftdrop.com";

        for (int i = 0; i < 5; i++) {
            guard.recordFailure(email);
        }

        assertThat(guard.isAccountLocked(email)).isTrue();

        // Advance clock by 901 seconds (> 900s lockout duration)
        clock.advanceSeconds(901);

        assertThat(guard.isAccountLocked(email)).isFalse();
        assertThatCode(() -> guard.checkLoginAllowed(email)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Successful login resets failure counters")
    void successfulLogin_resetsFailureCounters() {
        String email = "david@swiftdrop.com";

        for (int i = 0; i < 4; i++) {
            guard.recordFailure(email);
        }
        assertThat(guard.getAccountFailureCount(email)).isEqualTo(4);

        guard.recordSuccess(email);

        assertThat(guard.getAccountFailureCount(email)).isEqualTo(0);
        assertThat(guard.isAccountLocked(email)).isFalse();

        // New attempts start fresh from 0
        guard.recordFailure(email);
        assertThat(guard.getAccountFailureCount(email)).isEqualTo(1);
    }

    @Test
    @DisplayName("Source IP threshold locks malicious IP across multiple victim accounts")
    void sourceIpThreshold_locksAttackerIp() {
        String attackerIp = "198.51.100.23";

        // Attacker sprays 20 different user accounts from the same IP
        for (int i = 1; i <= 20; i++) {
            String victimEmail = "user" + i + "@swiftdrop.com";
            guard.recordFailure(victimEmail, attackerIp);
        }

        assertThat(guard.getSourceFailureCount(attackerIp)).isEqualTo(20);
        assertThat(guard.isSourceLocked(attackerIp)).isTrue();

        // Attacker IP is blocked
        assertThatThrownBy(() -> guard.checkLoginAllowed("another_user@swiftdrop.com", attackerIp))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("IP address " + attackerIp);

        // A legitimate user logging in from a different IP is unaffected
        String legitimateIp = "203.0.113.50";
        assertThatCode(() -> guard.checkLoginAllowed("user1@swiftdrop.com", legitimateIp))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Old attempts outside sliding window are pruned automatically")
    void slidingWindow_prunesOldAttempts() {
        String email = "eva@swiftdrop.com";

        guard.recordFailure(email);
        guard.recordFailure(email);
        assertThat(guard.getAccountFailureCount(email)).isEqualTo(2);

        // Advance clock by 10 minutes (600s)
        clock.advanceSeconds(600);
        guard.recordFailure(email);
        assertThat(guard.getAccountFailureCount(email)).isEqualTo(3);

        // Advance clock by another 6 minutes (360s, total 960s from first 2 attempts)
        clock.advanceSeconds(360);

        // First 2 attempts fell outside 900s window; only the 3rd remains
        assertThat(guard.getAccountFailureCount(email)).isEqualTo(1);
    }
}
