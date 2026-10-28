package com.swiftdrop.integration.notification.dto;

import java.util.List;

public record NotificationPageResponse(
    List<NotificationDto> items,
    long totalElements,
    long unreadCount
) {}

