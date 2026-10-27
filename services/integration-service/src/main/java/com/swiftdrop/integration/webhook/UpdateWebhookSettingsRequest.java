package com.swiftdrop.integration.webhook;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateWebhookSettingsRequest(
        @NotNull(message = "Webhook URL must not be null")
        String url,

        @NotNull(message = "Active flag must not be null")
        Boolean active,

        List<String> subscribedEvents,

        Boolean regenerateSecret
) {
    public boolean isRegenerateSecret() {
        return Boolean.TRUE.equals(regenerateSecret);
    }
}
