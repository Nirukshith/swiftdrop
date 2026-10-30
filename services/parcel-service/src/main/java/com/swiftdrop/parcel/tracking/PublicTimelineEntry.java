package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelStatus;

import java.time.Instant;

/** One status change on the public page: no reason, actor or courier data. */
public record PublicTimelineEntry(ParcelStatus status, Instant occurredAt) {
}
