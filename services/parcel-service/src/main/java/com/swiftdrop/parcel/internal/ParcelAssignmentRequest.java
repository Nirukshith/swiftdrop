package com.swiftdrop.parcel.internal;

import jakarta.validation.constraints.NotBlank;

public record ParcelAssignmentRequest(
        @NotBlank(message = "Courier ID is required")
        String courierId,

        String assignedBy
) {}
