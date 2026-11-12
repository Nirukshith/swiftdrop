package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelResponse;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import com.swiftdrop.parcel.parcel.StatusHistoryView;

import java.time.Instant;
import java.util.ArrayList;
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
        List<PublicTimelineEntry> timeline = statusChanges(history).stream()
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
        List<StatusHistoryView> changes = statusChanges(history);
        List<ParcelHistoryEntry> entries = changes.stream()
                .map(h -> new ParcelHistoryEntry(
                        null, // status_history (M1) does not store the ADR 005 sequence per change
                        h == changes.get(0) && isSameStatus(h) ? null : h.fromStatus(),
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

    /**
     * The status changes of a parcel, oldest first. status_history (M1) also has rows that do not
     * change the status: the creation row (CREATED -> CREATED, shown as null -> CREATED) and, since
     * week 4, one row per courier assignment or reassignment (CREATED -> CREATED, with the courier
     * ids in the reason). The first such row is the creation; later ones are not status changes, so
     * they are left out. That keeps one timeline row per status and keeps courier ids off the page.
     */
    static List<StatusHistoryView> statusChanges(List<StatusHistoryView> history) {
        List<StatusHistoryView> changes = new ArrayList<>();
        for (StatusHistoryView h : history) {
            if (!isSameStatus(h) || changes.isEmpty()) {
                changes.add(h);
            }
        }
        return changes;
    }

    private static boolean isSameStatus(StatusHistoryView h) {
        return h.fromStatus() == h.toStatus();
    }

    private static Instant lastUpdatedAt(ParcelResponse parcel, List<PublicTimelineEntry> timeline) {
        if (!timeline.isEmpty()) {
            return timeline.get(timeline.size() - 1).occurredAt();
        }
        return parcel.updatedAt() != null ? parcel.updatedAt() : parcel.createdAt();
    }
}
