package com.swiftdrop.operations.dispatch.client;

public class ParcelNotFoundException extends RuntimeException {

    public ParcelNotFoundException(String message) {
        super(message);
    }
}
