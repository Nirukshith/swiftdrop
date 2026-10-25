package com.swiftdrop.integration.assistant.dto;

import java.time.Instant;

public record RecentParcelItem(
    String trackingNumber,
    String status,
    String recipientName,
    Instant createdAt
) {}
