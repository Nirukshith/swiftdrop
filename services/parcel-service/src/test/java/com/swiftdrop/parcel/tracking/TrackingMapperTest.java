package com.swiftdrop.parcel.tracking;

import com.swiftdrop.parcel.address.AddressDto;
import com.swiftdrop.parcel.parcel.ParcelResponse;
import com.swiftdrop.parcel.parcel.ParcelSize;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import com.swiftdrop.parcel.parcel.StatusHistoryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingMapperTest {

    private static final Instant T0 = Instant.parse("2026-10-28T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-28T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-28T10:30:00Z");

    private static ParcelResponse parcel(String recipientName) {
        return new ParcelResponse(
                "7e9b3a24-c189-4b68-b7c1-fa9e6a0d2f11", "SD-20261028-MAP001", ParcelStatus.IN_TRANSIT,
                "cust-1", null, "courier-1",
                new AddressDto("45 Galle Road", null, "Colombo 03", "Colombo", "00300"),
                new AddressDto("12 Temple Road", "Flat 2", "Kandy", "Kandy", "20000"),
                new BigDecimal("2.50"), ParcelSize.MEDIUM, new BigDecimal("750.00"), 0, null,
                recipientName, "+94771234567", "Leave at the gate", null, 3L, T0, T2.plusSeconds(60));
    }

    private static final List<StatusHistoryView> HISTORY = List.of(
            new StatusHistoryView(ParcelStatus.CREATED, ParcelStatus.CREATED, "Parcel order created", T0),
            new StatusHistoryView(ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, T1),
            new StatusHistoryView(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, null, T2));

    @Test
    @DisplayName("Names are masked to the first letter of each part plus ***")
    void masksNames() {
        assertThat(TrackingMapper.maskName("Sunil Silva")).isEqualTo("S*** S***");
        assertThat(TrackingMapper.maskName("Kavindu")).isEqualTo("K***");
        assertThat(TrackingMapper.maskName("  Nimal   Perera  ")).isEqualTo("N*** P***");
        assertThat(TrackingMapper.maskName("A Very Long Fivepart Name")).isEqualTo("A*** V*** L*** F*** N***");
        assertThat(TrackingMapper.maskName("சுனில்")).isEqualTo("ச***");
        assertThat(TrackingMapper.maskName("")).isEqualTo("***");
        assertThat(TrackingMapper.maskName(null)).isEqualTo("***");
    }

    @Test
    @DisplayName("Public view holds only allow-listed fields")
    void publicView() {
        PublicTrackingResponse view = TrackingMapper.toPublic(parcel("Sunil Silva"), HISTORY);

        assertThat(view.trackingNumber()).isEqualTo("SD-20261028-MAP001");
        assertThat(view.status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(view.recipientNameMasked()).isEqualTo("S*** S***");
        assertThat(view.dropoffDistrict()).isEqualTo("Kandy");
        assertThat(view.lastUpdatedAt()).isEqualTo(T2);
        assertThat(view.timeline()).containsExactly(
                new PublicTimelineEntry(ParcelStatus.CREATED, T0),
                new PublicTimelineEntry(ParcelStatus.PICKED_UP, T1),
                new PublicTimelineEntry(ParcelStatus.IN_TRANSIT, T2));
        assertThat(view.toString())
                .doesNotContain("Temple Road", "Flat 2", "20000", "+9477", "750", "cust-1", "courier-1",
                        "Parcel order created", "Leave at the gate", "7e9b3a24");
    }

    @Test
    @DisplayName("Without history, lastUpdatedAt falls back to the parcel's updatedAt")
    void publicViewWithoutHistory() {
        PublicTrackingResponse view = TrackingMapper.toPublic(parcel("Sunil Silva"), List.of());
        assertThat(view.timeline()).isEmpty();
        assertThat(view.lastUpdatedAt()).isEqualTo(T2.plusSeconds(60));
    }

    @Test
    @DisplayName("History maps the creation entry's fromStatus to null and keeps sequence null")
    void historyView() {
        ParcelHistoryResponse history = TrackingMapper.toHistory(parcel("Sunil Silva"), HISTORY);

        assertThat(history.parcelId()).isEqualTo("7e9b3a24-c189-4b68-b7c1-fa9e6a0d2f11");
        assertThat(history.currentStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(history.entries()).containsExactly(
                new ParcelHistoryEntry(null, null, ParcelStatus.CREATED, T0, "Parcel order created"),
                new ParcelHistoryEntry(null, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, T1, null),
                new ParcelHistoryEntry(null, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, T2, null));
    }

    @Test
    @DisplayName("A re-queue (FAILED -> CREATED) keeps its fromStatus")
    void requeueKeepsFromStatus() {
        ParcelHistoryResponse history = TrackingMapper.toHistory(parcel("X"), List.of(
                new StatusHistoryView(ParcelStatus.FAILED, ParcelStatus.CREATED, "Re-queued", T1)));
        assertThat(history.entries().get(0).fromStatus()).isEqualTo(ParcelStatus.FAILED);
    }
}
