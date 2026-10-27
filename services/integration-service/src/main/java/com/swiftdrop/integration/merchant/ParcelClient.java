package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ParcelClient {
    InternalParcelResponse createParcel(UUID merchantId, CreateMerchantOrderRequest request,
                                        BigDecimal fee, String downstreamIdempotencyKey);

    InternalParcelResponse cancelParcel(UUID parcelId, String reason, String downstreamIdempotencyKey);

    record InternalParcelResponse(String id, String trackingNumber, String status,
                                  String merchantId, BigDecimal fee, Instant createdAt, Instant updatedAt) {}
}
