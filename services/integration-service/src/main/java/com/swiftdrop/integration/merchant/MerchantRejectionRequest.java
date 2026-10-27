package com.swiftdrop.integration.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantRejectionRequest(@NotBlank @Size(max = 1000) String reason) {}
