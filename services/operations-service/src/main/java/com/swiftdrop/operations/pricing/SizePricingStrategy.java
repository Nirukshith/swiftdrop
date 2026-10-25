package com.swiftdrop.operations.pricing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public final class SizePricingStrategy implements PricingStrategy {

    private final Map<String, BigDecimal> chargesBySize;

    public SizePricingStrategy(Map<String, BigDecimal> chargesBySize) {
        Objects.requireNonNull(chargesBySize, "chargesBySize must not be null");
        if (chargesBySize.isEmpty()) {
            throw new IllegalArgumentException("chargesBySize must not be empty");
        }
        chargesBySize.forEach((size, charge) -> {
            if (size == null || size.isBlank()) {
                throw new IllegalArgumentException("size keys must not be blank");
            }
            if (charge == null || charge.signum() < 0) {
                throw new IllegalArgumentException("size charges must not be null or negative");
            }
        });
        this.chargesBySize = Map.copyOf(chargesBySize);
    }

    @Override
    public PricingComponent calculate(QuoteRequest request) {
        BigDecimal charge = chargesBySize.get(request.size());
        if (charge == null) {
            throw new PricingException("UNKNOWN_SIZE", "Unknown parcel size: " + request.size());
        }
        return new PricingComponent("sizeCharge", charge);
    }
}
