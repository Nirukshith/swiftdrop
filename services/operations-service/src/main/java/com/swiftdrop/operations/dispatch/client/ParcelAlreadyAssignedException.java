package com.swiftdrop.operations.dispatch.client;

public class ParcelAlreadyAssignedException extends RuntimeException {

    public ParcelAlreadyAssignedException(String message) {
        super(message);
    }
}
