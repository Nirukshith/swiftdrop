package com.swiftdrop.operations.courier.execution.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CourierStatusUpdateResponse(
    UUID parcelId,
    String trackingNumber,
    String previousStatus,
    String currentStatus,
    Instant updatedAt,
    List<String> allowedNextStatuses
) {}
