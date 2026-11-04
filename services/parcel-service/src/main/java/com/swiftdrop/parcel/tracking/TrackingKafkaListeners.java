package com.swiftdrop.parcel.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The two consumers of parcel.status-changed.v1 (docs/tracking-design.md section 4).
 * <ul>
 *   <li><b>History writer</b>, shared group {@code parcel-tracking} (topics.md): each event is stored
 *       once across all instances; a repeated eventId is skipped. Failures are retried, then sent to the DLT.</li>
 *   <li><b>Live broadcaster</b>, one group per instance with {@code auto.offset.reset=latest}: every
 *       instance sees every event and pushes it to its own open streams. Best effort, nothing stored;
 *       a missed push is recovered by Last-Event-ID replay.</li>
 * </ul>
 */
@Component
class TrackingKafkaListeners {

    static final String TOPIC = "parcel.status-changed.v1";
    static final String WRITER_ID = "tracking-history-writer";
    static final String BROADCASTER_ID = "tracking-broadcaster";

    /**
     * On when Kafka is configured (spring.kafka.bootstrap-servers, set by the local profile and Compose);
     * swiftdrop.tracking.kafka.enabled overrides it. Contexts without a broker (most tests) do not start consumers.
     */
    static final String AUTO_STARTUP =
            "${swiftdrop.tracking.kafka.enabled:#{environment.containsProperty('spring.kafka.bootstrap-servers')}}";

    private static final Logger log = LoggerFactory.getLogger(TrackingKafkaListeners.class);

    private final TrackingEventParser parser;
    private final TrackingEventStore store;
    private final TrackingEmitterRegistry registry;

    TrackingKafkaListeners(TrackingEventParser parser, TrackingEventStore store, TrackingEmitterRegistry registry) {
        this.parser = parser;
        this.store = store;
        this.registry = registry;
    }

    @KafkaListener(
            id = WRITER_ID,
            idIsGroup = false,
            groupId = "parcel-tracking",
            topics = TOPIC,
            containerFactory = TrackingKafkaConfig.WRITER_CONTAINER_FACTORY,
            properties = "auto.offset.reset=earliest", // a new group catches up on the topic instead of skipping it
            autoStartup = AUTO_STARTUP)
    void storeStatusChange(String payload) {
        parser.parse(payload).ifPresent(change -> {
            if (!store.save(change)) {
                log.debug("Skipped already stored tracking event {}", change.eventId());
            }
        });
    }

    @KafkaListener(
            id = BROADCASTER_ID,
            idIsGroup = false,
            groupId = "${swiftdrop.tracking.broadcast-group-id:parcel-tracking-sse-${HOSTNAME:${random.uuid}}}",
            topics = TOPIC,
            properties = "auto.offset.reset=latest",
            autoStartup = AUTO_STARTUP)
    void broadcastStatusChange(String payload) {
        try {
            parser.parse(payload).ifPresent(change -> registry.publish(change.toPublic()));
        } catch (InvalidTrackingEventException e) {
            log.warn("Ignored unusable status-changed event for live tracking: {}", e.getMessage());
        }
    }
}
