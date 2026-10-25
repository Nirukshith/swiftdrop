package com.swiftdrop.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginGuardTest {

    @Test
    @DisplayName("NoOpLoginGuard allows login checks and records events without throwing")
    void testNoOpLoginGuardAllowsAll() {
        LoginGuard guard = new NoOpLoginGuard();

        assertThatCode(() -> {
            guard.checkLoginAllowed("user@swiftdrop.com");
            guard.checkLoginAllowed("user@swiftdrop.com", "192.168.1.100");
            guard.recordFailure("user@swiftdrop.com");
            guard.recordFailure("user@swiftdrop.com", "192.168.1.100");
            guard.recordSuccess("user@swiftdrop.com");
            guard.recordSuccess("user@swiftdrop.com", "192.168.1.100");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Custom LoginGuard implementation correctly throws AccountLockedException")
    void testCustomLoginGuardThrowsAccountLockedException() {
        LoginGuard lockingGuard = new LoginGuard() {
            @Override
            public void checkLoginAllowed(String email) {
                throw new AccountLockedException(email, 1729865700L);
            }

            @Override
            public void recordSuccess(String email) {}

            @Override
            public void recordFailure(String email) {}
        };

        assertThatThrownBy(() -> lockingGuard.checkLoginAllowed("victim@swiftdrop.com"))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("victim@swiftdrop.com");

        assertThatThrownBy(() -> lockingGuard.checkLoginAllowed("victim@swiftdrop.com", "10.0.0.1"))
                .isInstanceOf(AccountLockedException.class);
    }
}
