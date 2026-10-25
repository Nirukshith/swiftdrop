package com.swiftdrop.operations.pricing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public final class ZonePricingStrategy implements PricingStrategy {

    private final Map<String, Zone> zonesByDistrict;

    public ZonePricingStrategy(Map<String, Zone> zonesByDistrict) {
        Objects.requireNonNull(zonesByDistrict, "zonesByDistrict must not be null");
        this.zonesByDistrict = Map.copyOf(zonesByDistrict);
    }

    @Override
    public PricingComponent calculate(QuoteRequest request) {
        Zone pickupZone = findZone(request.pickupDistrict());
        if (!pickupZone.served()) {
            throw new PricingException(
                    "PICKUP_DISTRICT_UNSERVED",
                    "Pickup district is not an active served area: " + request.pickupDistrict());
        }

        Zone dropoffZone = findZone(request.dropoffDistrict());
        return new PricingComponent("dropoffZoneBaseFee", dropoffZone.baseFeeLkr());
    }

    private Zone findZone(String district) {
        Zone zone = zonesByDistrict.get(district);
        if (zone == null) {
            throw new PricingException("UNKNOWN_DISTRICT", "Unknown district: " + district);
        }
        return zone;
    }

    public record Zone(boolean served, BigDecimal baseFeeLkr) {

        public Zone {
            Objects.requireNonNull(baseFeeLkr, "baseFeeLkr must not be null");
            if (baseFeeLkr.signum() < 0) {
                throw new IllegalArgumentException("baseFeeLkr must not be negative");
            }
        }
    }
}
