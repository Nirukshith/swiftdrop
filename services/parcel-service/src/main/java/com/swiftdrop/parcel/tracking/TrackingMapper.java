package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelResponse;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import com.swiftdrop.parcel.parcel.StatusHistoryView;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the tracking DTOs. The public view is built from an allow-list of fields,
 * never by nulling fields on ParcelResponse, so a field added to it later cannot leak.
 */
final class TrackingMapper {

    private static final String MASK = "***";

    private TrackingMapper() {
    }

    static PublicTrackingResponse toPublic(ParcelResponse parcel, List<StatusHistoryView> history) {
        List<PublicTimelineEntry> timeline = history.stream()
                .map(h -> new PublicTimelineEntry(h.toStatus(), h.occurredAt()))
                .toList();
        String district = parcel.dropoffAddress() != null ? parcel.dropoffAddress().district() : null;
        return new PublicTrackingResponse(
                parcel.trackingNumber(),
                parcel.status(),
                maskName(parcel.recipientName()),
                district,
                lastUpdatedAt(parcel, timeline),
                timeline);
    }

    static ParcelHistoryResponse toHistory(ParcelResponse parcel, List<StatusHistoryView> history) {
        List<ParcelHistoryEntry> entries = history.stream()
                .map(h -> new ParcelHistoryEntry(
                        null, // the ADR 005 sequence is not stored per change until week 3
                        isCreation(h) ? null : h.fromStatus(),
                        h.toStatus(),
                        h.occurredAt(),
                        h.reason()))
                .toList();
        return new ParcelHistoryResponse(parcel.id(), parcel.trackingNumber(), parcel.status(), entries);
    }

    /** "Sunil Silva" becomes "S*** S***". A fixed "***" hides the length of each part. */
    static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return MASK;
        }
        return Arrays.stream(name.trim().split("\\s+"))
                .map(part -> new String(Character.toChars(part.codePointAt(0))) + MASK)
                .collect(Collectors.joining(" "));
    }

    /** The creation entry is stored as CREATED -> CREATED; the contract shows it as null -> CREATED. */
    private static boolean isCreation(StatusHistoryView h) {
        return h.fromStatus() == ParcelStatus.CREATED && h.toStatus() == ParcelStatus.CREATED;
    }

    private static Instant lastUpdatedAt(ParcelResponse parcel, List<PublicTimelineEntry> timeline) {
        if (!timeline.isEmpty()) {
            return timeline.get(timeline.size() - 1).occurredAt();
        }
        return parcel.updatedAt() != null ? parcel.updatedAt() : parcel.createdAt();
    }
}
