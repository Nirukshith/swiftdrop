package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class HttpPricingQuoteClient implements PricingQuoteClient {
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private final RestClient restClient;
    private final PricingAccessToken tokens;
    private final String internalPricingToken;

    @Autowired
    public HttpPricingQuoteClient(
            @Value("${services.operations.base-url:http://localhost:8083}") String baseUrl,
            PricingAccessToken tokens,
            @Value("${swiftdrop.security.internal.tokens.pricing-service:}") String internalPricingToken) {
        this(RestClient.builder().baseUrl(baseUrl).build(), tokens, internalPricingToken);
    }

    HttpPricingQuoteClient(RestClient restClient, PricingAccessToken tokens) {
        this(restClient, tokens, "");
    }

    HttpPricingQuoteClient(RestClient restClient, PricingAccessToken tokens, String internalPricingToken) {
        this.restClient = restClient;
        this.tokens = tokens;
        this.internalPricingToken = internalPricingToken;
    }

    @Override
    public BigDecimal quote(CreateMerchantOrderRequest request) {
        // Explicit OAuth configuration wins. Invalid/incomplete OAuth never silently falls back.
        String token = tokens.hasConfiguration() ? tokens.get() : null;
        if (token == null && internalPricingToken.isBlank()) {
            throw new IllegalStateException("Merchant pricing service credentials are not configured");
        }
        QuoteResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/internal/v1/pricing/quote")
                            .queryParam("pickupDistrict", request.pickupAddress().district())
                            .queryParam("dropoffDistrict", request.dropoffAddress().district())
                            .queryParam("weightKg", request.weightKg())
                            .queryParam("size", request.size().name())
                            .build())
                    .headers(headers -> {
                        if (token != null) headers.setBearerAuth(token);
                        else headers.set(INTERNAL_TOKEN_HEADER, internalPricingToken);
                    })
                    .retrieve()
                    .body(QuoteResponse.class);
        } catch (HttpClientErrorException.Unauthorized exception) {
            if (token != null) tokens.invalidate(token);
            throw exception;
        }
        if (response == null || !"LKR".equals(response.currency())
                || response.totalFee() == null || response.totalFee().signum() < 0) {
            throw new IllegalStateException("Pricing service returned an invalid quote");
        }
        return response.totalFee();
    }
}
