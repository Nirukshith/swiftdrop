package com.swiftdrop.operations.dispatch.client;

public class ParcelNotEligibleForRequeueException extends RuntimeException {
    public ParcelNotEligibleForRequeueException(String message) {
        super(message);
    }
}

