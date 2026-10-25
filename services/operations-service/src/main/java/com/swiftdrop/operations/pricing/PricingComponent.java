package com.swiftdrop.operations.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public record PricingComponent(String name, BigDecimal amount) {

    public PricingComponent {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }
}
