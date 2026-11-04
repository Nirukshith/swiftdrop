package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingSubscriberTest {

    private static final String TN = "SD-20261104-SUB001";

    private final CapturingEmitter emitter = new CapturingEmitter();
    private final AtomicInteger closeCalls = new AtomicInteger();

    private TrackingSubscriber subscriber(long lastSeen) {
        return new TrackingSubscriber(TN, emitter, lastSeen, s -> closeCalls.incrementAndGet());
    }

    private static PublicTrackingEvent event(long sequence, ParcelStatus status) {
        return new PublicTrackingEvent(TN, sequence, status, Instant.parse("2026-11-04T08:00:00Z").plusSeconds(sequence));
    }

    @Test
    @DisplayName("Sends retry, then status frames with the sequence as id and the masked JSON body")
    void frameFormat() {
        TrackingSubscriber s = subscriber(0);
        s.sendReconnectDelay(Duration.ofSeconds(5));
        s.replay(event(3, ParcelStatus.PICKED_UP));
        s.finishStart();

        assertThat(emitter.frames.get(0)).isEqualTo("retry:5000\n\n");
        assertThat(emitter.frames.get(1)).startsWith("id:3\nevent:status\ndata:").contains("PICKED_UP");
    }

    @Test
    @DisplayName("Live events during start-up are held back and sent afterwards in order, without duplicates")
    void holdsBackLiveEventsDuringStart() {
        TrackingSubscriber s = subscriber(2);
        s.deliver(event(5, ParcelStatus.OUT_FOR_DELIVERY)); // live, while replay runs
        s.replay(event(3, ParcelStatus.PICKED_UP));
        s.replay(event(4, ParcelStatus.IN_TRANSIT));
        s.deliver(event(4, ParcelStatus.IN_TRANSIT));       // live copy of a replayed event
        assertThat(emitter.sentIds()).containsExactly(3L, 4L);

        s.finishStart();
        assertThat(emitter.sentIds()).containsExactly(3L, 4L, 5L);
    }

    @Test
    @DisplayName("Events at or below the last sent sequence (Last-Event-ID or redelivery) are dropped")
    void dropsOldAndDuplicateSequences() {
        TrackingSubscriber s = subscriber(4);
        s.finishStart();
        s.deliver(event(3, ParcelStatus.PICKED_UP));
        s.deliver(event(4, ParcelStatus.IN_TRANSIT));
        s.deliver(event(6, ParcelStatus.OUT_FOR_DELIVERY));
        s.deliver(event(6, ParcelStatus.OUT_FOR_DELIVERY));
        s.deliver(event(5, ParcelStatus.IN_TRANSIT)); // out of order, older than what was sent

        assertThat(emitter.sentIds()).containsExactly(6L);
        assertThat(s.lastSentSequence()).isEqualTo(6);
    }

    @Test
    @DisplayName("A terminal status completes the stream; nothing is sent afterwards")
    void completesOnTerminalStatus() {
        TrackingSubscriber s = subscriber(0);
        s.finishStart();
        s.deliver(event(6, ParcelStatus.DELIVERED));
        s.deliver(event(7, ParcelStatus.CANCELLED));

        assertThat(emitter.sentIds()).containsExactly(6L);
        assertThat(emitter.completed).isTrue();
        assertThat(s.isClosed()).isTrue();
        assertThat(closeCalls).hasValue(1);
    }

    @Test
    @DisplayName("FAILED keeps the stream open (a dispatcher may re-queue the parcel)")
    void failedKeepsStreamOpen() {
        TrackingSubscriber s = subscriber(0);
        s.finishStart();
        s.deliver(event(5, ParcelStatus.FAILED));
        s.deliver(event(6, ParcelStatus.CREATED));

        assertThat(emitter.sentIds()).containsExactly(5L, 6L);
        assertThat(emitter.completed).isFalse();
    }

    @Test
    @DisplayName("A failed write closes only this subscriber and reports it once")
    void sendFailureClosesSubscriber() {
        TrackingSubscriber s = subscriber(0);
        s.finishStart();
        emitter.failNextSend = true;
        s.deliver(event(5, ParcelStatus.IN_TRANSIT));
        s.deliver(event(6, ParcelStatus.OUT_FOR_DELIVERY));
        s.heartbeat();

        assertThat(s.isClosed()).isTrue();
        assertThat(emitter.frames).isEmpty();
        assertThat(closeCalls).hasValue(1);
    }

    @Test
    @DisplayName("Heartbeats are comments and are only sent after start-up")
    void heartbeat() {
        TrackingSubscriber s = subscriber(0);
        s.heartbeat();
        assertThat(emitter.frames).isEmpty();

        s.finishStart();
        s.heartbeat();
        assertThat(emitter.frames).containsExactly(":heartbeat\n\n");
    }
}
