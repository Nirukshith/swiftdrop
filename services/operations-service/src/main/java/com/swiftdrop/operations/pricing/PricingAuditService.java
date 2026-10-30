package com.swiftdrop.operations.pricing;

import com.swiftdrop.operations.zones.ZoneAuditService;
import org.springframework.stereotype.Service;

@Service
public class PricingAuditService {

    private final ZoneAuditService delegate;

    public PricingAuditService(ZoneAuditService delegate) {
        this.delegate = delegate;
    }

    public void record(String action, String targetId) {
        delegate.record(action, targetId);
    }
}
