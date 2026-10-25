package com.swiftdrop.integration.merchant;

/** Parcel lifecycle status carried by parcel-service events and query updates. */
public enum ParcelStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    CANCELLED
}
