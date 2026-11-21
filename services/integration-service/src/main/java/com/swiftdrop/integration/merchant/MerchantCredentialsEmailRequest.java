package com.swiftdrop.integration.merchant;

import jakarta.validation.constraints.NotBlank;

public record MerchantCredentialsEmailRequest(
        @NotBlank String clientId,
        @NotBlank String clientSecret
) {}

