package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MerchantOrderResponse(UUID orderId, UUID parcelId, String trackingNumber,
                                    String externalReference, String status,
                                    BigDecimal fee, Instant createdAt) {
    public static MerchantOrderResponse from(MerchantOrderEntity entity) {
        return new MerchantOrderResponse(entity.getOrderId(), entity.getParcelId(), entity.getTrackingNumber(),
                entity.getExternalReference(), entity.getStatus().name(), entity.getFee(), entity.getCreatedAt());
    }
}
