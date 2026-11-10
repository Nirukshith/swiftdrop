package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MerchantOrderListItem(
        UUID orderId,
        String parcelId,
        String trackingNumber,
        MerchantOrderStatus status,
        String recipientName,
        String recipientAddress,
        BigDecimal fee,
        Double weightKg,
        Instant createdAt,
        Instant updatedAt) {

    public static MerchantOrderListItem from(MerchantOrder order) {
        return new MerchantOrderListItem(
                order.orderId(),
                order.parcelId() != null ? order.parcelId().toString() : null,
                order.trackingNumber(),
                order.status(),
                null,
                null,
                order.fee(),
                null,
                order.createdAt(),
                order.updatedAt()
        );
    }
}

