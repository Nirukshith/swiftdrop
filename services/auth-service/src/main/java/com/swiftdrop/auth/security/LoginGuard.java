package com.swiftdrop.auth.security;

/**
 * In-process contract for brute-force defense, login rate-limiting, and temporary account lockout.
 *
 * <p>Invoked by auth-service core authentication handlers (M10) prior to password validation
 * (to prevent hash computation DoS) and following success or failure to maintain sliding counters.
 *
 * <p>Owner: Member M7 (Security & Lockout)
 */
public interface LoginGuard {

    /**
     * Checks whether authentication is allowed for the specified user email.
     *
     * @param email user email attempting authentication
     * @throws AccountLockedException if maximum failed attempts exceeded
     */
    void checkLoginAllowed(String email);

    /**
     * IP-aware overload checking whether authentication is allowed for the given email and remote IP address.
     *
     * @param email     user email attempting authentication
     * @param ipAddress client remote IP address
     * @throws AccountLockedException if account or IP is temporarily restricted
     */
    default void checkLoginAllowed(String email, String ipAddress) {
        checkLoginAllowed(email);
    }

    /**
     * Records a successful login, resetting failure counters for the identifier.
     *
     * @param email user email
     */
    void recordSuccess(String email);

    /**
     * IP-aware overload recording a successful login.
     *
     * @param email     user email
     * @param ipAddress client remote IP address
     */
    default void recordSuccess(String email, String ipAddress) {
        recordSuccess(email);
    }

    /**
     * Records a failed login attempt, incrementing counters and locking if threshold reached.
     *
     * @param email user email
     */
    void recordFailure(String email);

    /**
     * IP-aware overload recording a failed login attempt.
     *
     * @param email     user email
     * @param ipAddress client remote IP address
     */
    default void recordFailure(String email, String ipAddress) {
        recordFailure(email);
    }
}
