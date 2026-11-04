package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelQueryService;
import com.swiftdrop.parcel.parcel.ParcelResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Opens public SSE streams (parcel-read.yaml {@code /api/v1/tracking/{trackingNo}/stream}).
 * <ol>
 *   <li>Unknown or malformed tracking number: the same 404 as the lookup.</li>
 *   <li>The stream is registered first, so live events that arrive during start-up are held back, not lost.</li>
 *   <li>With Last-Event-ID, the stored status changes after that sequence are replayed in order.</li>
 *   <li>Then the current state is sent if it is newer than anything the client has (always on a new connection).</li>
 * </ol>
 */
@Service
class TrackingStreamService {

    private final ParcelQueryService parcelQueryService;
    private final TrackingEventStore store;
    private final TrackingEmitterRegistry registry;
    private final TrackingStreamProperties properties;

    TrackingStreamService(ParcelQueryService parcelQueryService, TrackingEventStore store,
                          TrackingEmitterRegistry registry, TrackingStreamProperties properties) {
        this.parcelQueryService = parcelQueryService;
        this.store = store;
        this.registry = registry;
        this.properties = properties;
    }

    SseEmitter open(String trackingNumber, String lastEventId) {
        if (trackingNumber == null || !TrackingService.TRACKING_NUMBER.matcher(trackingNumber).matches()
                || parcelQueryService.findByTrackingNumber(trackingNumber).isEmpty()) {
            throw new TrackingNotFoundException();
        }
        long lastSeen = parseLastEventId(lastEventId);

        SseEmitter emitter = new SseEmitter(properties.timeout().toMillis());
        TrackingSubscriber subscriber = new TrackingSubscriber(trackingNumber, emitter, lastSeen, registry::remove);
        if (!registry.register(subscriber)) {
            throw new TrackingStreamsExhaustedException();
        }
        try {
            subscriber.sendReconnectDelay(properties.reconnectDelay());
            if (lastSeen > 0) {
                store.findAfter(trackingNumber, lastSeen).forEach(subscriber::replay);
            }
            // Read after registering, so a change committed meanwhile arrives either here or live.
            parcelQueryService.findByTrackingNumber(trackingNumber)
                    .map(this::currentState)
                    .ifPresent(subscriber::replay);
        } catch (RuntimeException e) {
            subscriber.complete(); // frees the slot in the registry
            throw e;
        } finally {
            subscriber.finishStart();
        }
        return emitter;
    }

    /** Current status as a stream event; its id is the parcel's latest ADR 005 sequence. */
    private PublicTrackingEvent currentState(ParcelResponse parcel) {
        PublicTrackingResponse view = TrackingMapper.toPublic(parcel, parcelQueryService.findStatusHistory(parcel.id()));
        long sequence = parcel.sequence() != null ? parcel.sequence() : 1L;
        return new PublicTrackingEvent(parcel.trackingNumber(), sequence, parcel.status(), view.lastUpdatedAt());
    }

    /** Missing or unparseable means "send the current state" (contract and design 3.3). */
    static long parseLastEventId(String header) {
        if (header == null || header.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(header.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
