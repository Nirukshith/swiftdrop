package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelStatus;

import java.util.List;

/** Authenticated status timeline (parcel-read.yaml ParcelHistoryResponse). */
public record ParcelHistoryResponse(
        String parcelId,
        String trackingNumber,
        ParcelStatus currentStatus,
        List<ParcelHistoryEntry> entries
) {
}
