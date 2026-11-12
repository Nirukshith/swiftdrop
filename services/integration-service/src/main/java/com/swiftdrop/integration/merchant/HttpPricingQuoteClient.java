package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class HttpPricingQuoteClient implements PricingQuoteClient {

    private final RestClient restClient;
    private final PricingAccessToken tokens;

    @Autowired
    public HttpPricingQuoteClient(@Value("${services.operations.base-url:http://localhost:8083}") String baseUrl,
                                  PricingAccessToken tokens) {
        this(RestClient.builder().baseUrl(baseUrl).build(), tokens);
    }

    HttpPricingQuoteClient(RestClient restClient, PricingAccessToken tokens) {
        this.restClient = restClient;
        this.tokens = tokens;
    }

    @Override
    public BigDecimal quote(CreateMerchantOrderRequest request) {
        String token = tokens.get();
        QuoteResponse response;
        try {
            response = restClient.get()
                .uri(uri -> uri.path("/internal/v1/pricing/quote")
                        .queryParam("pickupDistrict", request.pickupAddress().district())
                        .queryParam("dropoffDistrict", request.dropoffAddress().district())
                        .queryParam("weightKg", request.weightKg())
                        .queryParam("size", request.size().name())
                        .build())
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(QuoteResponse.class);
        } catch (HttpClientErrorException.Unauthorized exception) {
            tokens.invalidate(token);
            throw exception;
        }
        if (response == null || !"LKR".equals(response.currency())
                || response.totalFee() == null || response.totalFee().signum() < 0) {
            throw new IllegalStateException("Pricing service returned an invalid quote");
        }
        return response.totalFee();
    }
}
