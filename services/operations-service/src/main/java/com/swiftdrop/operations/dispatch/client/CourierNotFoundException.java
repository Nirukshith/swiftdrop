package com.swiftdrop.operations.dispatch.client;

public class CourierNotFoundException extends RuntimeException {

    public CourierNotFoundException(String message) {
        super(message);
    }
}
