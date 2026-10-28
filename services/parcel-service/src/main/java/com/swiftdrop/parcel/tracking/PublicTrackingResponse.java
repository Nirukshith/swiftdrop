package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelStatus;

import java.time.Instant;
import java.util.List;

/**
 * Masked public tracking view (parcel-read.yaml PublicTrackingResponse).
 * Adding a field here needs a masking review (docs/tracking-design.md section 2).
 */
public record PublicTrackingResponse(
        String trackingNumber,
        ParcelStatus status,
        String recipientNameMasked,
        String dropoffDistrict,
        Instant lastUpdatedAt,
        List<PublicTimelineEntry> timeline
) {
}
