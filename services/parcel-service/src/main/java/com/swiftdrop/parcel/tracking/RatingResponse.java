package com.swiftdrop.parcel.tracking;

import java.time.Instant;

/** A stored delivery rating (parcel-read.yaml RatingResponse). */
record RatingResponse(
        String parcelId,
        int score,
        String comment,
        Instant createdAt
) {
}
