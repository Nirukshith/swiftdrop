package com.swiftdrop.parcel.tracking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Per-IP limit on the public tracking endpoints (docs/tracking-design.md section 6).
 * The gateway (Nginx) limits first; this is the in-app safety net.
 *
 * @param requestsPerMinute steady rate per client IP
 * @param burst             requests a client may send at once before the steady rate applies
 * @param maxTrackedClients cap on remembered IPs, so a flood of addresses cannot grow memory without bound
 * @param trustedProxies    IPs or CIDR ranges of the gateway. X-Forwarded-For is read only when the
 *                          request comes from one of these; otherwise the socket address is used.
 */
@ConfigurationProperties("swiftdrop.tracking.rate-limit")
public record TrackingRateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30") int requestsPerMinute,
        @DefaultValue("10") int burst,
        @DefaultValue("10000") int maxTrackedClients,
        @DefaultValue({"127.0.0.1/32", "::1/128"}) List<String> trustedProxies
) {
}
