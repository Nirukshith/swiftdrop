package com.swiftdrop.integration.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class SsrfException extends ResponseStatusException {

    private final boolean transientDnsFailure;

    public SsrfException(String reason) {
        this(reason, false);
    }

    public SsrfException(String reason, boolean transientDnsFailure) {
        super(HttpStatus.BAD_REQUEST, reason);
        this.transientDnsFailure = transientDnsFailure;
    }

    public boolean isTransientDnsFailure() {
        return transientDnsFailure;
    }
}
