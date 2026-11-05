package com.swiftdrop.parcel.tracking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

/** The parcel_rating table (V4): one rating per parcel. */
@Repository
class RatingStore {

    private final JdbcTemplate jdbc;

    RatingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Stores the rating unless the parcel already has one. The primary key makes this safe when
     * two requests race: only one insert wins.
     *
     * @return false when the parcel was already rated, so nothing changed
     */
    boolean insert(String customerId, RatingResponse rating) {
        int rows = jdbc.update("""
                INSERT INTO parcel_rating (parcel_id, customer_id, score, comment, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (parcel_id) DO NOTHING
                """,
                rating.parcelId(),
                customerId,
                rating.score(),
                rating.comment(),
                Timestamp.from(rating.createdAt()));
        return rows == 1;
    }

    Optional<RatingResponse> find(String parcelId) {
        return jdbc.query("""
                SELECT parcel_id, score, comment, created_at
                FROM parcel_rating
                WHERE parcel_id = ?
                """,
                (rs, i) -> new RatingResponse(
                        rs.getString("parcel_id"),
                        rs.getInt("score"),
                        rs.getString("comment"),
                        rs.getTimestamp("created_at").toInstant()),
                parcelId).stream().findFirst();
    }
}
