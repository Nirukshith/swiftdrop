package com.swiftdrop.auth.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record ClientTokenRequest(
    @NotBlank(message = "Client ID is required")
    String clientId,

    @NotBlank(message = "Client secret is required")
    String clientSecret
) {}
