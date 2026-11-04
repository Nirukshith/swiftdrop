package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.parcel.ParcelStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/** The tracking_event table (V3): status changes kept for SSE replay. */
@Repository
class TrackingEventStore {

    private final JdbcTemplate jdbc;

    TrackingEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return false when this eventId was already stored (redelivery), so nothing changed */
    boolean save(StatusChange change) {
        int rows = jdbc.update("""
                INSERT INTO tracking_event
                    (event_id, parcel_id, tracking_number, sequence, from_status, to_status, reason, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                change.eventId(),
                change.parcelId(),
                change.trackingNumber(),
                change.sequence(),
                change.fromStatus(),
                change.toStatus().name(),
                change.reason(),
                Timestamp.from(change.occurredAt()));
        return rows == 1;
    }

    /** Masked events of one parcel after the given sequence, oldest first. */
    List<PublicTrackingEvent> findAfter(String trackingNumber, long afterSequence) {
        return jdbc.query("""
                SELECT tracking_number, sequence, to_status, occurred_at
                FROM tracking_event
                WHERE tracking_number = ? AND sequence > ?
                ORDER BY sequence, occurred_at
                """,
                (rs, i) -> new PublicTrackingEvent(
                        rs.getString("tracking_number"),
                        rs.getLong("sequence"),
                        ParcelStatus.valueOf(rs.getString("to_status")),
                        rs.getTimestamp("occurred_at").toInstant()),
                trackingNumber, afterSequence);
    }
}
