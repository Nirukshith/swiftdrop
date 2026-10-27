package com.swiftdrop.parcel.parcel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PricingQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(PricingQuoteClient.class);
    private final RestClient restClient;
    private final String quoteUrl;

    public PricingQuoteClient(
            RestClient.Builder restClientBuilder,
            @Value("${swiftdrop.pricing.quote-url:http://localhost:8083/internal/v1/pricing/quote}") String quoteUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.quoteUrl = quoteUrl;
    }

    public BigDecimal fetchQuote(String pickupDistrict, String dropoffDistrict, BigDecimal weightKg, ParcelSize size) {
        try {
            PricingQuoteResponse response = restClient.get()
                    .uri(quoteUrl, uriBuilder -> uriBuilder
                            .queryParam("pickupDistrict", pickupDistrict)
                            .queryParam("dropoffDistrict", dropoffDistrict)
                            .queryParam("weightKg", weightKg.toPlainString())
                            .queryParam("size", size.name())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.warn("Pricing quote endpoint returned status {}", res.getStatusCode());
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Pricing quote service error: " + res.getStatusCode());
                    })
                    .body(PricingQuoteResponse.class);

            if (response != null && response.totalFee() != null) {
                return response.totalFee().setScale(2, RoundingMode.HALF_UP);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Received empty fee from pricing quote service");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Failed to fetch quote from pricing service at {}: {}", quoteUrl, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pricing service unavailable: " + ex.getMessage(), ex);
        }
    }
}
