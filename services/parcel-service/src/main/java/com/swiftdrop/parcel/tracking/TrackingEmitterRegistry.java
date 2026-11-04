package com.swiftdrop.parcel.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Open SSE streams of this instance, by tracking number (docs/tracking-design.md section 3.4).
 * Streams remove themselves when they complete, time out or fail. One daemon thread sends the
 * heartbeat to every open stream.
 */
@Component
class TrackingEmitterRegistry implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TrackingEmitterRegistry.class);

    private final Map<String, Set<TrackingSubscriber>> streams = new ConcurrentHashMap<>();
    private final AtomicInteger openStreams = new AtomicInteger();
    private final int maxOpenStreams;
    private final ScheduledExecutorService heartbeat;

    TrackingEmitterRegistry(TrackingStreamProperties properties) {
        this.maxOpenStreams = properties.maxOpenStreams();
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tracking-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        long period = properties.heartbeat().toMillis();
        heartbeat.scheduleAtFixedRate(this::sendHeartbeats, period, period, TimeUnit.MILLISECONDS);
    }

    /** @return false when this instance already holds the maximum number of streams */
    boolean register(TrackingSubscriber subscriber) {
        if (openStreams.incrementAndGet() > maxOpenStreams) {
            openStreams.decrementAndGet();
            return false;
        }
        streams.compute(subscriber.trackingNumber(), (key, set) -> {
            Set<TrackingSubscriber> target = set != null ? set : ConcurrentHashMap.newKeySet();
            target.add(subscriber);
            return target;
        });
        return true;
    }

    void remove(TrackingSubscriber subscriber) {
        streams.computeIfPresent(subscriber.trackingNumber(), (key, set) -> {
            if (set.remove(subscriber)) {
                openStreams.decrementAndGet();
            }
            return set.isEmpty() ? null : set;
        });
    }

    /** Sends a live event to the streams of its parcel only. */
    void publish(PublicTrackingEvent event) {
        Set<TrackingSubscriber> subscribers = streams.get(event.trackingNumber());
        if (subscribers != null) {
            List.copyOf(subscribers).forEach(subscriber -> subscriber.deliver(event));
        }
    }

    int openStreams() {
        return openStreams.get();
    }

    int openStreams(String trackingNumber) {
        Set<TrackingSubscriber> subscribers = streams.get(trackingNumber);
        return subscribers == null ? 0 : subscribers.size();
    }

    /** Never throws: an exception would cancel the scheduled heartbeat for every stream. */
    void sendHeartbeats() {
        streams.values().forEach(set -> List.copyOf(set).forEach(subscriber -> {
            try {
                subscriber.heartbeat();
            } catch (RuntimeException e) {
                log.debug("Heartbeat failed for a tracking stream: {}", e.getMessage());
                subscriber.complete();
            }
        }));
    }

    @Override
    public void destroy() {
        heartbeat.shutdownNow();
        streams.values().forEach(set -> List.copyOf(set).forEach(TrackingSubscriber::complete));
    }
}
