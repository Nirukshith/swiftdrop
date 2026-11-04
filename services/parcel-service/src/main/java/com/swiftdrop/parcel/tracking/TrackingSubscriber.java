package com.swiftdrop.parcel.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * One open SSE stream. Sends each sequence at most once and in increasing order, so a live
 * event that was also replayed (or redelivered by Kafka) is not sent twice.
 * <p>
 * While the stream is starting (replay of missed events, then the current state), live events
 * are held back and sent afterwards, so nothing that happens during the replay is lost.
 * The stream completes after a terminal status (DELIVERED, CANCELLED).
 */
final class TrackingSubscriber {

    static final String EVENT_NAME = "status";

    private static final Logger log = LoggerFactory.getLogger(TrackingSubscriber.class);

    private final String trackingNumber;
    private final SseEmitter emitter;
    private final Consumer<TrackingSubscriber> onClose;

    private long lastSentSequence;
    private boolean starting = true;
    private List<PublicTrackingEvent> heldBack = new ArrayList<>();
    private boolean closed;

    /**
     * @param lastSeenSequence the client's Last-Event-ID, or 0 for a new connection
     * @param onClose          called once when the stream ends for any reason
     */
    TrackingSubscriber(String trackingNumber, SseEmitter emitter, long lastSeenSequence, Consumer<TrackingSubscriber> onClose) {
        this.trackingNumber = trackingNumber;
        this.emitter = emitter;
        this.lastSentSequence = lastSeenSequence;
        this.onClose = onClose;
        emitter.onCompletion(this::markClosed);
        emitter.onTimeout(this::complete);
        emitter.onError(error -> markClosed());
    }

    String trackingNumber() {
        return trackingNumber;
    }

    SseEmitter emitter() {
        return emitter;
    }

    synchronized long lastSentSequence() {
        return lastSentSequence;
    }

    synchronized boolean isClosed() {
        return closed;
    }

    /** Sends {@code retry: <ms>} once at connect. */
    synchronized void sendReconnectDelay(Duration delay) {
        trySend(SseEmitter.event().reconnectTime(delay.toMillis()));
    }

    /** Event from the start-up replay; sent now. */
    synchronized void replay(PublicTrackingEvent event) {
        sendIfNew(event);
    }

    /** Live event from Kafka; held back until start-up has finished. */
    synchronized void deliver(PublicTrackingEvent event) {
        if (closed) {
            return;
        }
        if (starting) {
            heldBack.add(event);
            return;
        }
        sendIfNew(event);
    }

    /** Ends start-up and sends the live events that arrived meanwhile, in sequence order. */
    synchronized void finishStart() {
        starting = false;
        List<PublicTrackingEvent> pending = heldBack;
        heldBack = null;
        if (pending == null) {
            return; // already closed
        }
        pending.stream().sorted(Comparator.comparingLong(PublicTrackingEvent::sequence)).forEach(this::sendIfNew);
    }

    synchronized void heartbeat() {
        if (!closed && !starting) {
            trySend(SseEmitter.event().comment("heartbeat"));
        }
    }

    synchronized void complete() {
        if (!closed) {
            markClosed();
            emitter.complete();
        }
    }

    private void sendIfNew(PublicTrackingEvent event) {
        if (closed || event.sequence() <= lastSentSequence) {
            return;
        }
        boolean sent = trySend(SseEmitter.event()
                .id(Long.toString(event.sequence()))
                .name(EVENT_NAME)
                .data(event, MediaType.APPLICATION_JSON));
        if (sent) {
            lastSentSequence = event.sequence();
            if (event.isTerminal()) {
                complete();
            }
        }
    }

    private boolean trySend(SseEmitter.SseEventBuilder builder) {
        if (closed) {
            return false;
        }
        try {
            emitter.send(builder);
            return true;
        } catch (IOException | IllegalStateException e) {
            // Client went away or the stream already ended: drop this subscriber only.
            log.debug("Tracking stream for {} closed while sending: {}", trackingNumber, e.getMessage());
            markClosed();
            try {
                emitter.completeWithError(e);
            } catch (RuntimeException ignored) {
                // already completed by the container
            }
            return false;
        }
    }

    private synchronized void markClosed() {
        if (!closed) {
            closed = true;
            heldBack = null;
            onClose.accept(this);
        }
    }
}
