package com.swiftdrop.integration.merchant;

import java.time.Instant;
import java.util.UUID;

/** Read model returned by MerchantOrderQuery for merchant-owned orders. */
public record MerchantOrder(
        UUID orderId,
        UUID merchantId,
        UUID parcelId,
        String trackingNumber,
        String externalReference,
        MerchantOrderStatus status,
        Instant createdAt) {
}
