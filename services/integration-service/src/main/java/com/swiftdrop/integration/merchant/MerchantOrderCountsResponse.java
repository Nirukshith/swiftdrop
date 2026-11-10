package com.swiftdrop.integration.merchant;

public record MerchantOrderCountsResponse(
        long total,
        long created,
        long pickedUp,
        long inTransit,
        long outForDelivery,
        long delivered,
        long failed,
        long cancelled) {

    public static MerchantOrderCountsResponse from(MerchantOrderStatusCounts counts) {
        return new MerchantOrderCountsResponse(
                counts.total(),
                counts.created(),
                counts.pickedUp(),
                counts.inTransit(),
                counts.outForDelivery(),
                counts.delivered(),
                counts.failed(),
                counts.cancelled()
        );
    }
}

