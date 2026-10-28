package com.swiftdrop.integration.notification.client;

public class ContactResolutionException extends RuntimeException {
    public ContactResolutionException(String message) {
        super(message);
    }

    public ContactResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

