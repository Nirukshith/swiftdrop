package com.swiftdrop.parcel.parcel;

import com.swiftdrop.parcel.address.AddressDto;

import java.math.BigDecimal;
import java.time.Instant;

public record ParcelResponse(
        String id,
        String trackingNumber,
        ParcelStatus status,
        String customerId,
        String merchantId,
        String courierId,
        AddressDto pickupAddress,
        AddressDto dropoffAddress,
        BigDecimal weightKg,
        ParcelSize size,
        BigDecimal fee,
        int attemptCount,
        String failureReason,
        String recipientName,
        String recipientPhone,
        String notes,
        DeliveryProof proof,
        Long sequence,
        Instant createdAt,
        Instant updatedAt
) {
    public static ParcelResponse from(Parcel entity) {
        return new ParcelResponse(
                entity.getId(),
                entity.getTrackingNumber(),
                entity.getStatus(),
                entity.getCustomerId(),
                entity.getMerchantId(),
                entity.getCourierId(),
                entity.getPickupAddress() != null ? entity.getPickupAddress().toDto() : null,
                entity.getDropoffAddress() != null ? entity.getDropoffAddress().toDto() : null,
                entity.getWeightKg(),
                entity.getSize(),
                entity.getFee(),
                entity.getAttemptCount(),
                entity.getFailureReason(),
                entity.getRecipientName(),
                entity.getRecipientPhone(),
                entity.getNotes(),
                entity.getProof(),
                entity.getSequence(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
