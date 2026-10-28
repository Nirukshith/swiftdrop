package com.swiftdrop.auth.admin.dto;

import com.swiftdrop.auth.user.User;

import java.time.Instant;

public record UserSummary(
    String id,
    String email,
    String fullName,
    String phoneNumber,
    String role,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
    public static UserSummary from(User user) {
        return new UserSummary(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getPhoneNumber(),
            user.getRole(),
            user.isEnabled(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
