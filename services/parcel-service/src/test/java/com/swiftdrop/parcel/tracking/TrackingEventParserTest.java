package com.swiftdrop.parcel.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingEventParserTest {

    private final TrackingEventParser parser = new TrackingEventParser(new ObjectMapper().registerModule(new JavaTimeModule()));

    private static String event(String eventType, String sequence, String toStatus, String trackingNumber) {
        return """
                {"eventId":"e9b207e4-2395-46f1-a18a-986cf194ad53","eventType":"%s","occurredAt":"2026-11-04T09:30:00Z",
                 "sequence":%s,"parcelId":"7e9b3a24-c189-4b68-b7c1-fa9e6a0d2f11","trackingNumber":%s,
                 "fromStatus":"IN_TRANSIT","toStatus":"%s","courierId":"usr-courier-42","previousCourierId":null,
                 "merchantId":null,"customerId":"usr-cust-901","reason":"Recipient asked to call"}
                """.formatted(eventType, sequence, trackingNumber, toStatus);
    }

    @Test
    @DisplayName("Reads a PARCEL_STATUS_CHANGED event from the contract")
    void readsStatusChange() {
        StatusChange change = parser.parse(event("PARCEL_STATUS_CHANGED", "4", "OUT_FOR_DELIVERY", "\"SD-20261104-PRS001\"")).orElseThrow();

        assertThat(change.eventId()).isEqualTo("e9b207e4-2395-46f1-a18a-986cf194ad53");
        assertThat(change.sequence()).isEqualTo(4);
        assertThat(change.toStatus()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(change.occurredAt()).isEqualTo(Instant.parse("2026-11-04T09:30:00Z"));
        assertThat(change.toPublic()).isEqualTo(new PublicTrackingEvent(
                "SD-20261104-PRS001", 4, ParcelStatus.OUT_FOR_DELIVERY, Instant.parse("2026-11-04T09:30:00Z")));
        assertThat(change.toPublic().toString()).doesNotContain("usr-cust-901", "usr-courier-42", "Recipient asked");
    }

    @Test
    @DisplayName("Other event types are skipped")
    void skipsOtherTypes() {
        assertThat(parser.parse(event("PARCEL_ASSIGNED", "3", "PICKED_UP", "\"SD-20261104-PRS001\""))).isEmpty();
    }

    @Test
    @DisplayName("Broken or incomplete events are rejected (they go to the DLT, not retried)")
    void rejectsBadEvents() {
        assertThatThrownBy(() -> parser.parse("not json")).isInstanceOf(InvalidTrackingEventException.class);
        assertThatThrownBy(() -> parser.parse("null")).isInstanceOf(InvalidTrackingEventException.class);
        assertThatThrownBy(() -> parser.parse(event("PARCEL_STATUS_CHANGED", "0", "DELIVERED", "\"SD-20261104-PRS001\"")))
                .isInstanceOf(InvalidTrackingEventException.class);
        assertThatThrownBy(() -> parser.parse(event("PARCEL_STATUS_CHANGED", "null", "DELIVERED", "\"SD-20261104-PRS001\"")))
                .isInstanceOf(InvalidTrackingEventException.class);
        assertThatThrownBy(() -> parser.parse(event("PARCEL_STATUS_CHANGED", "5", "LOST", "\"SD-20261104-PRS001\"")))
                .isInstanceOf(InvalidTrackingEventException.class);
        assertThatThrownBy(() -> parser.parse(event("PARCEL_STATUS_CHANGED", "5", "DELIVERED", "null")))
                .isInstanceOf(InvalidTrackingEventException.class);
    }

    @Test
    @DisplayName("A missing or unparseable Last-Event-ID means a new connection")
    void lastEventId() {
        assertThat(TrackingStreamService.parseLastEventId(null)).isZero();
        assertThat(TrackingStreamService.parseLastEventId(" ")).isZero();
        assertThat(TrackingStreamService.parseLastEventId("abc")).isZero();
        assertThat(TrackingStreamService.parseLastEventId("-4")).isZero();
        assertThat(TrackingStreamService.parseLastEventId(" 7 ")).isEqualTo(7);
    }
}
