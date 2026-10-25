package com.swiftdrop.integration.merchant;

/** Lifecycle states for a merchant account. */
public enum MerchantStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    /**
     * Returns whether this state may transition directly to {@code next}.
     * State checking is pure Java and does not perform persistence or I/O.
     */
    public boolean canTransitionTo(MerchantStatus next) {
        if (next == null) {
            return false;
        }

        return switch (this) {
            case PENDING -> next == APPROVED || next == REJECTED;
            case APPROVED -> next == SUSPENDED;
            case REJECTED, SUSPENDED -> false;
        };
    }

    /** Orders may be placed only by an approved merchant. */
    public boolean canPlaceOrders() {
        return this == APPROVED;
    }
}
