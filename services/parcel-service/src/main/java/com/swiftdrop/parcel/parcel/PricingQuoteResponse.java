package com.swiftdrop.parcel.parcel;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PricingQuoteResponse(
        @JsonAlias({"totalFee", "totalFeeLkr", "fee"})
        BigDecimal totalFee,
        String currency
) {}
