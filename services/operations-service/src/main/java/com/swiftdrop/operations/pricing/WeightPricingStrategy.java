package com.swiftdrop.operations.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class WeightPricingStrategy implements PricingStrategy {

    private final List<WeightBand> bands;

    public WeightPricingStrategy(List<WeightBand> bands) {
        Objects.requireNonNull(bands, "bands must not be null");
        if (bands.isEmpty()) {
            throw new IllegalArgumentException("bands must not be empty");
        }
        this.bands = List.copyOf(bands);
    }

    @Override
    public PricingComponent calculate(QuoteRequest request) {
        WeightBand matchingBand = bands.stream()
                .filter(band -> band.contains(request.weightKg(), band == bands.get(bands.size() - 1)))
                .findFirst()
                .orElseThrow(() -> new PricingException(
                        "WEIGHT_OUT_OF_RANGE",
                        "No weight band matches: " + request.weightKg() + "kg"));
        return new PricingComponent("weightBandCharge", matchingBand.chargeLkr());
    }

    public record WeightBand(
            BigDecimal minimumKg,
            BigDecimal maximumKg,
            BigDecimal chargeLkr) {

        public WeightBand {
            Objects.requireNonNull(minimumKg, "minimumKg must not be null");
            Objects.requireNonNull(maximumKg, "maximumKg must not be null");
            Objects.requireNonNull(chargeLkr, "chargeLkr must not be null");
            if (minimumKg.signum() < 0 || maximumKg.compareTo(minimumKg) <= 0) {
                throw new IllegalArgumentException("weight band bounds are invalid");
            }
            if (chargeLkr.signum() < 0) {
                throw new IllegalArgumentException("chargeLkr must not be negative");
            }
        }

        private boolean contains(BigDecimal weightKg, boolean includeMaximum) {
            return weightKg.compareTo(minimumKg) >= 0
                    && (includeMaximum
                    ? weightKg.compareTo(maximumKg) <= 0
                    : weightKg.compareTo(maximumKg) < 0);
        }
    }
}
