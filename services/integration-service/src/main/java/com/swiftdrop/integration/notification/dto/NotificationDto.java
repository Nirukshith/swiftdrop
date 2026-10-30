package com.swiftdrop.integration.notification.dto;

import java.time.Instant;

public record NotificationDto(
    String id,
    String title,
    String message,
    String channel,
    String parcelId,
    String trackingNumber,
    boolean read,
    Instant createdAt
) {}

