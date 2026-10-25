package com.swiftdrop.operations.pricing;

import java.math.BigDecimal;
import java.util.List;

public record Quote(BigDecimal totalFeeLkr, List<PricingComponent> breakdown) {

    public Quote {
        breakdown = List.copyOf(breakdown);
    }
}
