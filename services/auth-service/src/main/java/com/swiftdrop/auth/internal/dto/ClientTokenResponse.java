package com.swiftdrop.auth.internal.dto;

public record ClientTokenResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {}
