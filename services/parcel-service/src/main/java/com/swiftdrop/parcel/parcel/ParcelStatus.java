package com.swiftdrop.parcel.parcel;

/**
 * Canonical lifecycle status for a SwiftDrop parcel.
 * In accordance with SRS Section 5.3 and Architecture Decision Record 005.
 */
public enum ParcelStatus {

    /**
     * Parcel delivery order created and validated.
     * Courier assignment may occur in this state without changing status.
     */
    CREATED,

    /**
     * Assigned courier has physically collected the parcel from sender/merchant.
     */
    PICKED_UP,

    /**
     * Parcel is in transit between hubs or towards destination distribution hub.
     */
    IN_TRANSIT,

    /**
     * Parcel is loaded onto courier vehicle for final-mile delivery.
     */
    OUT_FOR_DELIVERY,

    /**
     * Parcel successfully delivered to recipient. Terminal success state.
     */
    DELIVERED,

    /**
     * Delivery attempt failed (recipient absent, incorrect address, etc.).
     * May be re-queued back to CREATED by dispatcher.
     */
    FAILED,

    /**
     * Order cancelled prior to pickup by customer or merchant. Terminal cancellation state.
     */
    CANCELLED;

    /**
     * Checks if this status is terminal (cannot transition further).
     * DELIVERED and CANCELLED are strictly terminal.
     * FAILED is a terminal attempt but can be re-queued to CREATED by a dispatcher.
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }

    /**
     * Checks if the parcel in this status can be cancelled.
     * Per SRS Section 5.3, cancellation is permitted only prior to pickup (status CREATED).
     */
    public boolean canCancel() {
        return this == CREATED;
    }

    /**
     * Checks if the parcel in this status can be re-queued by a dispatcher.
     * Re-queue is permitted only from FAILED status.
     */
    public boolean canRequeue() {
        return this == FAILED;
    }
}
