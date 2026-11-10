package com.swiftdrop.integration.merchant;

public record MerchantOrderStatusCounts(
        long total,
        long created,
        long pickedUp,
        long inTransit,
        long outForDelivery,
        long delivered,
        long failed,
        long cancelled) {
}

