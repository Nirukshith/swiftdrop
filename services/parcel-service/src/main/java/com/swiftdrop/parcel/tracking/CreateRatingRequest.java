package com.swiftdrop.parcel.tracking;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/parcels/{id}/rating (parcel-read.yaml CreateRatingRequest). */
record CreateRatingRequest(
        @NotNull(message = "score is required")
        @Min(value = 1, message = "score must be between 1 and 5")
        @Max(value = 5, message = "score must be between 1 and 5")
        @JsonDeserialize(using = StrictIntegerDeserializer.class)
        Integer score,

        @Size(max = 500, message = "comment must be at most 500 characters")
        String comment
) {
}
