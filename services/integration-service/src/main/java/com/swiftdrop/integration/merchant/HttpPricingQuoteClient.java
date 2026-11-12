package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpPricingQuoteClient implements PricingQuoteClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;
    private final String internalPricingToken;

    public HttpPricingQuoteClient(
            @Value("${services.operations.base-url:http://localhost:8083}") String baseUrl,
            @Value("${swiftdrop.security.internal.tokens.pricing-service:swiftdrop-internal-pricing-secret-2026}") String internalPricingToken) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalPricingToken = internalPricingToken;
    }

    @Override
    public BigDecimal quote(CreateMerchantOrderRequest request) {
        QuoteResponse response = restClient.get()
                .uri(uri -> uri.path("/internal/v1/pricing/quote")
                        .queryParam("pickupDistrict", request.pickupAddress().district())
                        .queryParam("dropoffDistrict", request.dropoffAddress().district())
                        .queryParam("weightKg", request.weightKg())
                        .queryParam("size", request.size().name())
                        .build())
                .header(INTERNAL_TOKEN_HEADER, internalPricingToken)
                .retrieve()
                .body(QuoteResponse.class);
        if (response == null || !"LKR".equals(response.currency())
                || response.totalFee() == null || response.totalFee().signum() < 0) {
            throw new IllegalStateException("Pricing service returned an invalid quote");
        }
        return response.totalFee();
    }
}
