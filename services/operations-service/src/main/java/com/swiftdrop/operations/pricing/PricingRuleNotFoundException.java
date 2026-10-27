package com.swiftdrop.operations.pricing;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PricingRuleNotFoundException extends RuntimeException {

    public PricingRuleNotFoundException(UUID id) {
        super("Pricing rule not found: " + id);
    }
}
