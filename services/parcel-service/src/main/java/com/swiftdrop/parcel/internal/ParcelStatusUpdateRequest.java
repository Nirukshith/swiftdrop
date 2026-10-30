package com.swiftdrop.parcel.internal;

import com.swiftdrop.parcel.parcel.DeliveryProof;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import jakarta.validation.constraints.NotNull;

public record ParcelStatusUpdateRequest(
        @NotNull(message = "Target status is required")
        ParcelStatus status,

        @NotNull(message = "Expected current status is required for concurrency control")
        ParcelStatus expectedCurrentStatus,

        String courierId,

        String reason,

        DeliveryProof proof,

        String idempotencyKey
) {}
