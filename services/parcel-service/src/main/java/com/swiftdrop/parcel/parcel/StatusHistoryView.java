package com.swiftdrop.parcel.parcel;

import java.time.Instant;

/**
 * Read-only copy of a status_history row. The creation row is stored as CREATED -> CREATED;
 * readers decide how to present it.
 */
public record StatusHistoryView(
        ParcelStatus fromStatus,
        ParcelStatus toStatus,
        String reason,
        Instant occurredAt
) {
    static StatusHistoryView from(StatusHistory entity) {
        return new StatusHistoryView(entity.getFromStatus(), entity.getToStatus(), entity.getReason(), entity.getCreatedAt());
    }
}
