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

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUserId(String userId);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.consumedAt = :consumedAt WHERE t.userId = :userId AND t.consumedAt IS NULL")
    void invalidateActiveTokensForUser(@Param("userId") String userId, @Param("consumedAt") Instant consumedAt);
}

