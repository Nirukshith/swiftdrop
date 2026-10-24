package com.swiftdrop.common;

import java.time.Instant;

public record ErrorResponse(
    String code,
    String message,
    String correlationId,
    Instant timestamp
) {}
