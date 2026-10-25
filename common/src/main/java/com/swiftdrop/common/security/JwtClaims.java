package com.swiftdrop.common.security;

public final class JwtClaims {

    private JwtClaims() {
        // Prevent instantiation
    }

    public static final String SUB = "sub";
    public static final String EMAIL = "email";
    public static final String ROLE = "role";
    public static final String ROLES = "roles";
    public static final String MERCHANT_ID = "merchantId";
    public static final String SCOPE = "scope";
    public static final String SCP = "scp";
}
