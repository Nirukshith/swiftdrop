package com.swiftdrop.integration.assistant.dto;

import java.math.BigDecimal;

public record FeeEstimateResult(
    Double weightKg,
    String sizeCategory,
    String destinationDistrict,
    BigDecimal estimatedFee,
    String currency
) {}
