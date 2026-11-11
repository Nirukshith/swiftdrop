package com.swiftdrop.operations.dispatch.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignParcelRequest(
    @NotBlank(message = "parcelId is required")
    String parcelId,

    @NotBlank(message = "courierId is required")
    String courierId,

    Boolean reassign
) {
    public AssignParcelRequest(String parcelId, String courierId) {
        this(parcelId, courierId, false);
    }
}
