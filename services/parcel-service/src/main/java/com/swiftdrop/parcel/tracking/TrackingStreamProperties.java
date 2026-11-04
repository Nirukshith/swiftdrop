package com.swiftdrop.parcel.tracking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Public SSE stream settings (parcel-read.yaml, docs/tracking-design.md section 3).
 *
 * @param heartbeat      interval of the {@code :heartbeat} comment that keeps proxies from closing idle streams
 * @param timeout        stream lifetime; the browser then reconnects with Last-Event-ID
 * @param reconnectDelay the {@code retry:} value sent at connect
 * @param maxOpenStreams cap per instance (NF-02); above it new streams get 503
 */
@ConfigurationProperties("swiftdrop.tracking.stream")
public record TrackingStreamProperties(
        @DefaultValue("15s") Duration heartbeat,
        @DefaultValue("30m") Duration timeout,
        @DefaultValue("5s") Duration reconnectDelay,
        @DefaultValue("500") int maxOpenStreams
) {
}
