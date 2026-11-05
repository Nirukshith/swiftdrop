package com.swiftdrop.parcel.receipt;

import com.swiftdrop.parcel.address.AddressDto;
import com.swiftdrop.parcel.parcel.ParcelResponse;
import com.swiftdrop.parcel.parcel.ParcelSize;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What a receipt shows (docs/tracking-design.md section 8). Built only from persisted parcel
 * data. No phone number, notes, proof photo or courier data.
 */
record ReceiptData(
        String trackingNumber,
        String parcelId,
        Instant createdAt,
        Instant deliveredAt,
        AddressDto pickupAddress,
        AddressDto dropoffAddress,
        String recipientName,
        ParcelSize size,
        BigDecimal weightKg,
        BigDecimal fee,
        Instant generatedAt
) {
    static ReceiptData of(ParcelResponse parcel, Instant deliveredAt, Instant generatedAt) {
        return new ReceiptData(
                parcel.trackingNumber(),
                parcel.id(),
                parcel.createdAt(),
                deliveredAt,
                parcel.pickupAddress(),
                parcel.dropoffAddress(),
                parcel.recipientName(),
                parcel.size(),
                parcel.weightKg(),
                parcel.fee(),
                generatedAt);
    }
}
