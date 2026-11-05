package com.swiftdrop.operations.dispatch.dto;

import java.time.Instant;

public record RequeueResponse(
    String parcelId,
    String status,
    int attemptCount,
    Instant requeuedAt
) {}
