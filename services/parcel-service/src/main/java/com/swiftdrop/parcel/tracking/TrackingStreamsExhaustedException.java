package com.swiftdrop.parcel.tracking;

/** This instance already holds the maximum number of open streams (docs/tracking-design.md 3.4). */
class TrackingStreamsExhaustedException extends RuntimeException {

    static final String MESSAGE = "Live tracking is busy, please retry later";

    TrackingStreamsExhaustedException() {
        super(MESSAGE);
    }
}
