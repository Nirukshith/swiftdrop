package com.swiftdrop.operations.courier.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FailedDeliveryRequest(
    @NotNull FailureReasonCode reasonCode,
    String note
) {
    public void validate() {
        if (reasonCode == FailureReasonCode.OTHER && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("A note is required when reasonCode is OTHER");
        }
    }
}
