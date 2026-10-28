package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;

public interface PricingQuoteClient {
    BigDecimal quote(CreateMerchantOrderRequest request);

    record QuoteResponse(String currency, BigDecimal totalFee) {}
}
