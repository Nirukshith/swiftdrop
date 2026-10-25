package com.swiftdrop.operations.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public final class QuoteCalculator {

    private static final int MONEY_SCALE = 2;

    private final List<PricingStrategy> strategies;

    public QuoteCalculator(List<PricingStrategy> strategies) {
        Objects.requireNonNull(strategies, "strategies must not be null");
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("strategies must not be empty");
        }
        this.strategies = List.copyOf(strategies);
    }

    public Quote calculate(QuoteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<PricingComponent> breakdown = strategies.stream()
                .map(strategy -> strategy.calculate(request))
                .toList();
        BigDecimal total = breakdown.stream()
                .map(PricingComponent::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new Quote(total, breakdown);
    }
}
