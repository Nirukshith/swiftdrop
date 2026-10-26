package com.swiftdrop.parcel.parcel;

import java.util.Collections;
import java.util.Set;

/**
 * Exception thrown when a requested parcel status transition violates the state machine rules.
 */
public class IllegalStateTransitionException extends IllegalStateException {

    private final ParcelStatus fromStatus;
    private final ParcelStatus toStatus;
    private final Set<ParcelStatus> allowedNextStatuses;

    public IllegalStateTransitionException(ParcelStatus fromStatus, ParcelStatus toStatus, Set<ParcelStatus> allowedNextStatuses) {
        super(String.format("Cannot transition parcel from %s to %s. Allowed transitions from %s: %s",
                fromStatus, toStatus, fromStatus, allowedNextStatuses));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.allowedNextStatuses = allowedNextStatuses != null ? Collections.unmodifiableSet(allowedNextStatuses) : Collections.emptySet();
    }

    public ParcelStatus getFromStatus() {
        return fromStatus;
    }

    public ParcelStatus getToStatus() {
        return toStatus;
    }

    public Set<ParcelStatus> getAllowedNextStatuses() {
        return allowedNextStatuses;
    }
}
