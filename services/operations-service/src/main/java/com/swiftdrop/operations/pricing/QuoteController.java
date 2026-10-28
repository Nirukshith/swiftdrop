package com.swiftdrop.operations.pricing;

import com.swiftdrop.operations.zones.Districts;
import com.swiftdrop.operations.zones.Zone;
import com.swiftdrop.operations.zones.ZoneRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/pricing")
@PreAuthorize("hasRole('DISPATCHER')")
public class QuoteController {

    private final ZoneRepository zoneRepository;
    private final PricingRuleRepository ruleRepository;

    public QuoteController(ZoneRepository zoneRepository, PricingRuleRepository ruleRepository) {
        this.zoneRepository = zoneRepository;
        this.ruleRepository = ruleRepository;
    }

    @GetMapping("/quote")
    public QuoteResponse quote(@Valid @ModelAttribute QuoteQuery query) {
        Districts.validate(query.pickupDistrict());
        Districts.validate(query.dropoffDistrict());
        Zone pickup = zoneRepository.findByDistrictsContaining(query.pickupDistrict())
                .orElseThrow(() -> new PricingException("UNKNOWN_DISTRICT", "Unknown district: " + query.pickupDistrict()));
        Zone dropoff = zoneRepository.findByDistrictsContaining(query.dropoffDistrict())
                .orElseThrow(() -> new PricingException("UNKNOWN_DISTRICT", "Unknown district: " + query.dropoffDistrict()));
        Map<String, ZonePricingStrategy.Zone> zones = new HashMap<>();
        zones.put(query.pickupDistrict(), new ZonePricingStrategy.Zone(pickup.isServed(), pickup.getBaseFeeLkr()));
        zones.put(query.dropoffDistrict(), new ZonePricingStrategy.Zone(dropoff.isServed(), dropoff.getBaseFeeLkr()));
        List<PricingRule> activeRules = ruleRepository.findByActiveTrue();
        List<WeightPricingStrategy.WeightBand> bands = activeRules.stream()
                .filter(rule -> rule.getKind() == PricingRule.Kind.WEIGHT_BAND)
                .map(rule -> new WeightPricingStrategy.WeightBand(rule.getMinimumWeightKg(),
                        rule.getMaximumWeightKg(), rule.getChargeLkr())).toList();
        Map<String, BigDecimal> sizes = activeRules.stream()
                .filter(rule -> rule.getKind() == PricingRule.Kind.SIZE)
                .collect(java.util.stream.Collectors.toMap(PricingRule::getKey, PricingRule::getChargeLkr));
        Quote quote = new QuoteCalculator(List.of(
                new ZonePricingStrategy(zones),
                new WeightPricingStrategy(bands),
                new SizePricingStrategy(sizes))).calculate(
                new QuoteRequest(query.pickupDistrict(), query.dropoffDistrict(), query.weightKg(), query.size()));
        Map<String, BigDecimal> breakdown = quote.breakdown().stream()
                .collect(java.util.stream.Collectors.toMap(PricingComponent::name, PricingComponent::amount));
        return new QuoteResponse("LKR", quote.totalFeeLkr(), new Breakdown(
                breakdown.get("dropoffZoneBaseFee"), breakdown.get("weightBandCharge"), breakdown.get("sizeCharge")),
                pickup.getName(), dropoff.getName());
    }

    public record QuoteQuery(
            @NotBlank String pickupDistrict,
            @NotBlank String dropoffDistrict,
            @DecimalMin(value = "0.0001") BigDecimal weightKg,
            @NotBlank String size) {
    }

    public record QuoteResponse(
            String currency, BigDecimal totalFee, Breakdown breakdown,
            String pickupZone, String dropoffZone) {
    }

    public record Breakdown(
            BigDecimal dropoffZoneBaseFee, BigDecimal weightBandCharge, BigDecimal sizeCharge) {
    }
}
