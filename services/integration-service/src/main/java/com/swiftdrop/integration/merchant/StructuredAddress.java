package com.swiftdrop.integration.merchant;

import jakarta.validation.constraints.NotBlank;

public record StructuredAddress(
        @NotBlank String line1,
        String line2,
        @NotBlank String city,
        @NotBlank String district,
        String postalCode) {
}
