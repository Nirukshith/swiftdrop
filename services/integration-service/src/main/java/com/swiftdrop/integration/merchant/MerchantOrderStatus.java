package com.swiftdrop.integration.merchant;

/** Merchant dashboard view of the parcel lifecycle status. */
public enum MerchantOrderStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    CANCELLED
}
