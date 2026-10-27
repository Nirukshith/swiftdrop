package com.swiftdrop.auth.internal.dto;

import java.time.Instant;

public record InternalUserResponse(
    String id,
    String email,
    String fullName,
    String phoneNumber,
    String role,
    String merchantId,
    boolean enabled,
    Instant createdAt
) {}
