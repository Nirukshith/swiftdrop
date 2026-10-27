package com.swiftdrop.auth.dto;

public record TokenRefreshResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {}
