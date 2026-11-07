package com.swiftdrop.auth.reset.dto;

public record ForgotPasswordResponse(
    String message
) {
    public static final String DEFAULT_MESSAGE =
        "If an account exists with this email, password reset instructions have been sent.";

    public ForgotPasswordResponse() {
        this(DEFAULT_MESSAGE);
    }
}

