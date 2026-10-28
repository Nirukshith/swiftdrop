package com.swiftdrop.integration.notification.channel;

import java.time.Instant;

public record NotificationMessage(
    String id,
    String eventId,
    String userId,
    String parcelId,
    String trackingNumber,
    String recipientTarget,
    String title,
    String content,
    Long sequence,
    Instant timestamp
) {}

