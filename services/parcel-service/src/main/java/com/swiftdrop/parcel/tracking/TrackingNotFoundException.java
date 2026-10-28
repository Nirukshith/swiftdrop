package com.swiftdrop.parcel.tracking;

/** Unknown or malformed tracking number. Both give the same 404 so numbers cannot be probed. */
class TrackingNotFoundException extends RuntimeException {

    static final String MESSAGE = "No parcel found for this tracking number";

    TrackingNotFoundException() {
        super(MESSAGE);
    }
}
