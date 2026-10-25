package com.swiftdrop.operations.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingStrategyTest {

    private static final QuoteRequest REQUEST =
            new QuoteRequest("Colombo", "Gampaha", new BigDecimal("2.00"), "MEDIUM");

    @Test
    void zoneStrategyUsesDropoffBaseFeeAfterValidatingPickupServiceability() {
        ZonePricingStrategy strategy = new ZonePricingStrategy(Map.of(
                "Colombo", new ZonePricingStrategy.Zone(true, new BigDecimal("500")),
                "Gampaha", new ZonePricingStrategy.Zone(true, new BigDecimal("650"))));

        assertEquals(new BigDecimal("650"), strategy.calculate(REQUEST).amount());
    }

    @Test
    void zoneStrategyRejectsUnknownDistrict() {
        ZonePricingStrategy strategy = new ZonePricingStrategy(Map.of(
                "Colombo", new ZonePricingStrategy.Zone(true, new BigDecimal("500"))));

        PricingException exception = assertThrows(
                PricingException.class,
                () -> strategy.calculate(REQUEST));

        assertEquals("UNKNOWN_DISTRICT", exception.code());
    }

    @Test
    void zoneStrategyRejectsUnservedPickupDistrict() {
        ZonePricingStrategy strategy = new ZonePricingStrategy(Map.of(
                "Colombo", new ZonePricingStrategy.Zone(false, new BigDecimal("500")),
                "Gampaha", new ZonePricingStrategy.Zone(true, new BigDecimal("650"))));

        PricingException exception = assertThrows(
                PricingException.class,
                () -> strategy.calculate(REQUEST));

        assertEquals("PICKUP_DISTRICT_UNSERVED", exception.code());
    }

    @Test
    void weightStrategyUsesInclusiveLowerAndExclusiveUpperBoundaries() {
        WeightPricingStrategy strategy = new WeightPricingStrategy(List.of(
                new WeightPricingStrategy.WeightBand(
                        new BigDecimal("0"), new BigDecimal("1"), new BigDecimal("100")),
                new WeightPricingStrategy.WeightBand(
                        new BigDecimal("1"), new BigDecimal("5"), new BigDecimal("200"))));

        assertEquals(new BigDecimal("100"), strategy.calculate(requestWithWeight("0.99")).amount());
        assertEquals(new BigDecimal("200"), strategy.calculate(requestWithWeight("1.00")).amount());
        assertEquals(new BigDecimal("200"), strategy.calculate(requestWithWeight("1.01")).amount());
        assertEquals(new BigDecimal("200"), strategy.calculate(requestWithWeight("5.00")).amount());
    }

    @Test
    void weightStrategyRejectsWeightAboveConfiguredBands() {
        WeightPricingStrategy strategy = new WeightPricingStrategy(List.of(
                new WeightPricingStrategy.WeightBand(
                        new BigDecimal("0"), new BigDecimal("5"), new BigDecimal("200"))));

        PricingException exception = assertThrows(
                PricingException.class,
                () -> strategy.calculate(requestWithWeight("5.01")));

        assertEquals("WEIGHT_OUT_OF_RANGE", exception.code());
    }

    @Test
    void sizeStrategyReturnsConfiguredChargeAndRejectsUnknownSize() {
        SizePricingStrategy strategy = new SizePricingStrategy(Map.of(
                "SMALL", new BigDecimal("50"),
                "MEDIUM", new BigDecimal("150")));

        assertEquals(new BigDecimal("150"), strategy.calculate(REQUEST).amount());
        PricingException exception = assertThrows(
                PricingException.class,
                () -> strategy.calculate(requestWithSize("LARGE")));
        assertEquals("UNKNOWN_SIZE", exception.code());
    }

    @Test
    void quoteCalculatorCombinesStrategiesAndRoundsTotalToTwoDecimals() {
        QuoteCalculator calculator = new QuoteCalculator(List.of(
                request -> new PricingComponent("zone", new BigDecimal("500.005")),
                request -> new PricingComponent("weight", new BigDecimal("200.004")),
                request -> new PricingComponent("size", new BigDecimal("149.995"))));

        Quote quote = calculator.calculate(REQUEST);

        assertEquals(new BigDecimal("850.00"), quote.totalFeeLkr());
        assertEquals(3, quote.breakdown().size());
    }

    private static QuoteRequest requestWithWeight(String weightKg) {
        return new QuoteRequest("Colombo", "Gampaha", new BigDecimal(weightKg), "MEDIUM");
    }

    private static QuoteRequest requestWithSize(String size) {
        return new QuoteRequest("Colombo", "Gampaha", new BigDecimal("2.00"), size);
    }
}
