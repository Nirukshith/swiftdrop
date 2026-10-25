package com.swiftdrop.operations.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public record QuoteRequest(
        String pickupDistrict,
        String dropoffDistrict,
        BigDecimal weightKg,
        String size) {

    public QuoteRequest {
        pickupDistrict = requireText(pickupDistrict, "pickupDistrict");
        dropoffDistrict = requireText(dropoffDistrict, "dropoffDistrict");
        size = requireText(size, "size");
        Objects.requireNonNull(weightKg, "weightKg must not be null");
        if (weightKg.signum() <= 0) {
            throw new IllegalArgumentException("weightKg must be greater than zero");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
