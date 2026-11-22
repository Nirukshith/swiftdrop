package com.swiftdrop.auth.reset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    /**
     * The user's most recent reset code that has not been used or invalidated.
     */
    Optional<PasswordResetToken> findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(String userId);

    List<PasswordResetToken> findByUserId(String userId);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.consumedAt = :consumedAt WHERE t.userId = :userId AND t.consumedAt IS NULL")
    void invalidateActiveTokensForUser(@Param("userId") String userId, @Param("consumedAt") Instant consumedAt);

    /**
     * Atomically records a failed guess so concurrent requests cannot lose increments.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.failedAttempts = t.failedAttempts + 1 WHERE t.id = :id")
    int incrementFailedAttempts(@Param("id") String id);

    /**
     * Invalidates the code once it has reached the failed-attempt limit.
     *
     * @return 1 if the code was locked by this call, otherwise 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.consumedAt = :consumedAt "
            + "WHERE t.id = :id AND t.consumedAt IS NULL AND t.failedAttempts >= :maxAttempts")
    int lockIfAttemptsExhausted(
            @Param("id") String id,
            @Param("maxAttempts") int maxAttempts,
            @Param("consumedAt") Instant consumedAt);
}
