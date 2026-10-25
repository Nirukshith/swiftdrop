package com.swiftdrop.integration.assistant.dto;

import java.time.Instant;

public record ParcelStatusResult(
    String trackingNumber,
    String status,
    String currentCity,
    String destinationCity,
    Instant lastUpdated
) {}
