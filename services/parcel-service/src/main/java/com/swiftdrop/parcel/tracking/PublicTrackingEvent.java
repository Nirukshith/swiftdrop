package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelStatus;

import java.time.Instant;

/**
 * Body of an SSE {@code status} event (parcel-read.yaml PublicTrackingEvent). Masked like the
 * public lookup: no customer, courier, reason or internal ids.
 */
public record PublicTrackingEvent(
        String trackingNumber,
        long sequence,
        ParcelStatus status,
        Instant occurredAt
) {
    boolean isTerminal() {
        return status == ParcelStatus.DELIVERED || status == ParcelStatus.CANCELLED;
    }
}
