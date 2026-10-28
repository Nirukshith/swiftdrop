package com.swiftdrop.operations.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.operations.zones.Zone;
import com.swiftdrop.operations.zones.ZoneRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuoteControllerContractTest {

    @Test
    void realControllerResponseMatchesWireMockQuoteShape() throws Exception {
        ZoneRepository zones = org.mockito.Mockito.mock(ZoneRepository.class);
        PricingRuleRepository rules = org.mockito.Mockito.mock(PricingRuleRepository.class);
        Zone pickup = new Zone("COLOMBO", Set.of("Colombo"), true, new BigDecimal("500.00"));
        Zone dropoff = new Zone("GAMPAHA", Set.of("Gampaha"), true, new BigDecimal("500.00"));
        when(zones.findByDistrictsContaining("Colombo")).thenReturn(java.util.Optional.of(pickup));
        when(zones.findByDistrictsContaining("Gampaha")).thenReturn(java.util.Optional.of(dropoff));
        when(rules.findByActiveTrue()).thenReturn(List.of(
                new PricingRule(PricingRule.Kind.WEIGHT_BAND, "UP_TO_5KG",
                        BigDecimal.ZERO, new BigDecimal("5.00"), new BigDecimal("200.00"), true),
                new PricingRule(PricingRule.Kind.SIZE, "MEDIUM", null, null,
                        new BigDecimal("150.00"), true)));

        QuoteController.QuoteResponse response = new QuoteController(zones, rules)
                .quote(new QuoteController.QuoteQuery("Colombo", "Gampaha",
                        new BigDecimal("2.50"), "MEDIUM"));
        JsonNode actual = new ObjectMapper().valueToTree(response);
        Path mappingPath = Path.of("..", "..", "contracts", "stubs", "pricing", "mappings", "pricing-quote.json");
        JsonNode expected = new ObjectMapper().readTree(Files.readString(mappingPath)).path("response").path("jsonBody");

        assertEquals(expected.path("currency"), actual.path("currency"));
        assertEquals(0, expected.path("totalFee").decimalValue().compareTo(actual.path("totalFee").decimalValue()));
        for (String component : List.of("dropoffZoneBaseFee", "weightBandCharge", "sizeCharge")) {
            assertEquals(0, expected.path("breakdown").path(component).decimalValue()
                    .compareTo(actual.path("breakdown").path(component).decimalValue()));
        }
        assertEquals(expected.path("pickupZone"), actual.path("pickupZone"));
        assertEquals(expected.path("dropoffZone"), actual.path("dropoffZone"));
    }
}
