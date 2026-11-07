package com.swiftdrop.auth.reset.dto;

public record ResetPasswordResponse(
    String message
) {
    public static final String DEFAULT_MESSAGE =
        "Password has been successfully reset. Please log in with your new password.";

    public ResetPasswordResponse() {
        this(DEFAULT_MESSAGE);
    }
}

