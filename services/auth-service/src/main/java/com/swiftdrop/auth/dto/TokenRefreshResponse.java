package com.swiftdrop.auth.dto;

public record TokenRefreshResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    UserSummary user
) {
    public TokenRefreshResponse(String accessToken, String tokenType, long expiresIn) {
        this(accessToken, tokenType, expiresIn, null);
    }
}
