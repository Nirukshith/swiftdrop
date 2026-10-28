package com.swiftdrop.operations.courier.execution.dto;

import jakarta.validation.constraints.NotBlank;

public record CourierStatusUpdateRequest(
    @NotBlank(message = "Status is required")
    String status,
    String reason,
    String recipientName,
    String idempotencyKey
) {}
