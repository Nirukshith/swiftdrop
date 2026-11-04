package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelStatus;

import java.time.Instant;

/** A validated PARCEL_STATUS_CHANGED event, as stored in tracking_event. */
record StatusChange(
        String eventId,
        String parcelId,
        String trackingNumber,
        long sequence,
        String fromStatus,
        ParcelStatus toStatus,
        String reason,
        Instant occurredAt
) {
    /** The only part that may leave the server on the public stream. */
    PublicTrackingEvent toPublic() {
        return new PublicTrackingEvent(trackingNumber, sequence, toStatus, occurredAt);
    }
}
