package com.swiftdrop.integration.merchant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateMerchantOrderRequest(
        @NotBlank @Size(max = 128) String externalReference,
        @NotBlank String recipientName,
        @NotBlank String recipientPhone,
        @NotNull @Valid StructuredAddress pickupAddress,
        @NotNull @Valid StructuredAddress dropoffAddress,
        @NotNull MerchantParcelSize size,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal weightKg) {
}
