package com.swiftdrop.auth.oauth2;

public record GoogleUserInfo(
        String sub,
        String email,
        String name,
        String picture,
        boolean emailVerified
) {}

