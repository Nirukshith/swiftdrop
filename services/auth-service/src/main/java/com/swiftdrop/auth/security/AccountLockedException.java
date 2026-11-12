package com.swiftdrop.auth.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an authentication attempt is rejected due to active account or IP lockout.
 * Maps to HTTP 423 Locked.
 *
 * <p>Owner: Member M7 (Security & Lockout)
 */
@ResponseStatus(HttpStatus.LOCKED)
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }

    public AccountLockedException(String email, long lockExpiryEpochSeconds) {
        super("Login temporarily restricted. Please try again later.");
    }
}
