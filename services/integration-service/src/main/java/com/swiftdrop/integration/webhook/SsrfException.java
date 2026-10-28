package com.swiftdrop.integration.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class SsrfException extends ResponseStatusException {

    public SsrfException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }
}
