package com.swiftdrop.parcel.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swiftdrop.parcel.parcel.ParcelStatus;

import java.time.Instant;

/**
 * One status change (parcel-read.yaml ParcelHistoryEntry). Null fields are always written:
 * the contract requires the sequence key even while it is null (until week 3).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ParcelHistoryEntry(
        Long sequence,
        ParcelStatus fromStatus,
        ParcelStatus toStatus,
        Instant occurredAt,
        String reason
) {
}
