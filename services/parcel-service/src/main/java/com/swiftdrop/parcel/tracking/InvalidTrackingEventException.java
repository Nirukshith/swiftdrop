package com.swiftdrop.parcel.tracking;

/** A status-changed message that cannot be used. Not retried: it goes straight to the DLT. */
class InvalidTrackingEventException extends RuntimeException {

    InvalidTrackingEventException(String message) {
        super(message);
    }

    InvalidTrackingEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
