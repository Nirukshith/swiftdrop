package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingEmitterRegistryTest {

    private static final String A = "SD-20261104-AAAAAA";
    private static final String B = "SD-20261104-BBBBBB";

    private final TrackingEmitterRegistry registry = new TrackingEmitterRegistry(
            new TrackingStreamProperties(Duration.ofHours(1), Duration.ofMinutes(30), Duration.ofSeconds(5), 3));

    @AfterEach
    void tearDown() {
        registry.destroy();
    }

    private record Stream(TrackingSubscriber subscriber, CapturingEmitter emitter) {
    }

    private Stream open(String trackingNumber) {
        CapturingEmitter emitter = new CapturingEmitter();
        TrackingSubscriber subscriber = new TrackingSubscriber(trackingNumber, emitter, 0, registry::remove);
        assertThat(registry.register(subscriber)).isTrue();
        subscriber.finishStart();
        return new Stream(subscriber, emitter);
    }

    private static PublicTrackingEvent event(String trackingNumber, long sequence) {
        return new PublicTrackingEvent(trackingNumber, sequence, ParcelStatus.IN_TRANSIT, Instant.now());
    }

    @Test
    @DisplayName("An event reaches every stream of its parcel and no other parcel")
    void publishesToTheRightParcel() {
        Stream a1 = open(A);
        Stream a2 = open(A);
        Stream b = open(B);

        registry.publish(event(A, 4));

        assertThat(a1.emitter().sentIds()).containsExactly(4L);
        assertThat(a2.emitter().sentIds()).containsExactly(4L);
        assertThat(b.emitter().frames).isEmpty();
    }

    @Test
    @DisplayName("Closed streams are removed and their slot is freed")
    void removesClosedStreams() {
        Stream a1 = open(A);
        open(A);
        assertThat(registry.openStreams()).isEqualTo(2);

        a1.subscriber().complete();

        assertThat(registry.openStreams()).isEqualTo(1);
        assertThat(registry.openStreams(A)).isEqualTo(1);
        registry.publish(event(A, 4));
        assertThat(a1.emitter().frames).isEmpty();
    }

    @Test
    @DisplayName("A stream whose client has gone is removed on the next write")
    void removesBrokenStreams() {
        Stream a = open(A);
        a.emitter().failNextSend = true;

        registry.publish(event(A, 4));

        assertThat(registry.openStreams()).isZero();
        assertThat(registry.openStreams(A)).isZero();
    }

    @Test
    @DisplayName("No more than maxOpenStreams per instance; a freed slot can be reused")
    void capsOpenStreams() {
        Stream first = open(A);
        open(A);
        open(B);
        TrackingSubscriber fourth = new TrackingSubscriber(B, new CapturingEmitter(), 0, registry::remove);

        assertThat(registry.register(fourth)).isFalse();
        assertThat(registry.openStreams()).isEqualTo(3);

        first.subscriber().complete();
        assertThat(registry.register(fourth)).isTrue();
    }

    @Test
    @DisplayName("A stream that throws during a heartbeat is closed; the others keep getting heartbeats")
    void heartbeatSurvivesBrokenStream() {
        Stream broken = open(A);
        Stream healthy = open(B);
        broken.emitter().unexpected = new UnsupportedOperationException("boom");

        registry.sendHeartbeats();
        registry.sendHeartbeats();

        assertThat(broken.subscriber().isClosed()).isTrue();
        assertThat(registry.openStreams(A)).isZero();
        assertThat(healthy.emitter().frames).hasSize(2);
    }

    @Test
    @DisplayName("Heartbeats go to every open stream; shutdown completes them all")
    void heartbeatAndShutdown() {
        Stream a = open(A);
        Stream b = open(B);

        registry.sendHeartbeats();
        assertThat(a.emitter().frames).containsExactly(":heartbeat\n\n");
        assertThat(b.emitter().frames).containsExactly(":heartbeat\n\n");

        registry.destroy();
        assertThat(a.emitter().completed).isTrue();
        assertThat(b.emitter().completed).isTrue();
        assertThat(registry.openStreams()).isZero();
    }
}
