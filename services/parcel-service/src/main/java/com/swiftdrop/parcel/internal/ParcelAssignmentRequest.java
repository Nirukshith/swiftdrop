package com.swiftdrop.parcel.internal;

import jakarta.validation.constraints.NotBlank;

public record ParcelAssignmentRequest(
        @NotBlank(message = "Courier ID is required")
        String courierId,

        String assignedBy,

        Boolean reassign
) {
    public ParcelAssignmentRequest(String courierId, String assignedBy) {
        this(courierId, assignedBy, false);
    }
}
