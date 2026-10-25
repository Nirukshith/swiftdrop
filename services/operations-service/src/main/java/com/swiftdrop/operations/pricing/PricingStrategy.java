package com.swiftdrop.operations.pricing;

@FunctionalInterface
public interface PricingStrategy {

    PricingComponent calculate(QuoteRequest request);
}
