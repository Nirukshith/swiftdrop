package com.swiftdrop.operations.dispatch.dto;

import java.time.Instant;

public record AssignmentResult(
    String parcelId,
    String courierId,
    Instant assignedAt,
    String status
) {}
