package com.swiftdrop.integration.webhook;

import java.util.List;

public record WebhookDeliveryLogPage(
        List<WebhookDeliveryLogItem> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
