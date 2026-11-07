package com.swiftdrop.auth.oauth2;

public interface GoogleOAuthClient {
    /**
     * Exchanges authorization code with Google token endpoint, validates the returned ID token,
     * and extracts verified user identity profile.
     *
     * @param code Single-use authorization code from Google
     * @return Verified user profile claims
     */
    GoogleUserInfo exchangeCodeAndGetUserInfo(String code);
}

