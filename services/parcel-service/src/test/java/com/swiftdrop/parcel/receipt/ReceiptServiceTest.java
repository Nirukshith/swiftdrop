package com.swiftdrop.parcel.receipt;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.address.AddressDto;
import com.swiftdrop.parcel.parcel.ParcelQueryService;
import com.swiftdrop.parcel.parcel.ParcelResponse;
import com.swiftdrop.parcel.parcel.ParcelSize;
import com.swiftdrop.parcel.parcel.ParcelStatus;
import com.swiftdrop.parcel.parcel.StatusHistoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptServiceTest {

    private static final String PARCEL_ID = "parcel-1";
    private static final String OWNER = "customer-1";
    private static final Instant NOW = Instant.parse("2026-10-30T04:30:00Z");

    private final ParcelQueryService parcelQueryService = mock(ParcelQueryService.class);
    private final ReceiptPdfRenderer renderer = mock(ReceiptPdfRenderer.class);
    private final ReceiptService service =
            new ReceiptService(parcelQueryService, renderer, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(renderer.render(any())).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
    }

    private static CurrentUser user(String id, String role) {
        return new CurrentUser(id, null, role, Set.of(role), null, Set.of("ROLE_" + role));
    }

    private static ParcelResponse parcel(ParcelStatus status) {
        return new ParcelResponse(PARCEL_ID, "SD-20261028-RCP001", status, OWNER, null, "courier-1",
                new AddressDto("45 Galle Road", null, "Colombo 03", "Colombo", "00300"),
                new AddressDto("12 Temple Road", null, "Kandy", "Kandy", "20000"),
                new BigDecimal("2.50"), ParcelSize.MEDIUM, new BigDecimal("750.00"), 0, null,
                "Sunil Silva", "+94771234567", "Leave at the gate", null, 5L,
                Instant.parse("2026-10-28T08:00:00Z"), Instant.parse("2026-10-29T12:00:00Z"));
    }

    private void given(ParcelStatus status, CurrentUser caller) {
        when(parcelQueryService.getAccessibleParcel(PARCEL_ID, caller)).thenReturn(parcel(status));
    }

    @Test
    @DisplayName("The owner gets the PDF with a receipt-<trackingNumber>.pdf file name and the delivery time")
    void ownerGetsReceipt() {
        CurrentUser owner = user(OWNER, "CUSTOMER");
        given(ParcelStatus.DELIVERED, owner);
        Instant deliveredAt = Instant.parse("2026-10-29T10:15:00Z");
        when(parcelQueryService.findStatusHistory(PARCEL_ID)).thenReturn(List.of(
                new StatusHistoryView(ParcelStatus.CREATED, ParcelStatus.CREATED, null, Instant.parse("2026-10-28T08:00:00Z")),
                new StatusHistoryView(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, null, deliveredAt)));

        ReceiptService.Receipt receipt = service.receipt(PARCEL_ID, owner);

        assertThat(receipt.fileName()).isEqualTo("receipt-SD-20261028-RCP001.pdf");
        assertThat(receipt.pdf()).startsWith('%', 'P', 'D', 'F');
        ArgumentCaptor<ReceiptData> data = ArgumentCaptor.forClass(ReceiptData.class);
        verify(renderer).render(data.capture());
        assertThat(data.getValue().deliveredAt()).isEqualTo(deliveredAt);
        assertThat(data.getValue().generatedAt()).isEqualTo(NOW);
        assertThat(data.getValue().trackingNumber()).isEqualTo("SD-20261028-RCP001");
        assertThat(data.getValue().fee()).isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("Without a DELIVERED history row the parcel's last update time is used")
    void deliveredAtFallsBackToUpdatedAt() {
        CurrentUser owner = user(OWNER, "CUSTOMER");
        given(ParcelStatus.DELIVERED, owner);
        when(parcelQueryService.findStatusHistory(PARCEL_ID)).thenReturn(List.of());

        service.receipt(PARCEL_ID, owner);

        ArgumentCaptor<ReceiptData> data = ArgumentCaptor.forClass(ReceiptData.class);
        verify(renderer).render(data.capture());
        assertThat(data.getValue().deliveredAt()).isEqualTo(Instant.parse("2026-10-29T12:00:00Z"));
    }

    @Test
    @DisplayName("ADMIN may download any delivered parcel's receipt")
    void adminAllowed() {
        CurrentUser admin = user("admin-1", "ADMIN");
        given(ParcelStatus.DELIVERED, admin);

        assertThat(service.receipt(PARCEL_ID, admin).pdf()).isNotEmpty();
    }

    @Test
    @DisplayName("The assigned courier and DISPATCHER can see the parcel but get 403 for the receipt")
    void courierAndDispatcherDenied() {
        for (CurrentUser caller : List.of(user("courier-1", "COURIER"), user("dispatcher-1", "DISPATCHER"))) {
            given(ParcelStatus.DELIVERED, caller);
            assertThatThrownBy(() -> service.receipt(PARCEL_ID, caller)).isInstanceOf(ReceiptAccessDeniedException.class);
        }
        verify(renderer, never()).render(any());
    }

    @Test
    @DisplayName("A customer who is not the owner gets 403 (from ParcelQueryService's access rule)")
    void otherCustomerDenied() {
        CurrentUser other = user("customer-2", "CUSTOMER");
        when(parcelQueryService.getAccessibleParcel(PARCEL_ID, other)).thenThrow(new AccessDeniedException("denied"));

        assertThatThrownBy(() -> service.receipt(PARCEL_ID, other)).isInstanceOf(ReceiptAccessDeniedException.class);
        verify(renderer, never()).render(any());
    }

    @Test
    @DisplayName("A customer token whose id does not match the stored owner is denied even if the parcel is returned")
    void ownershipComesFromStoredParcel() {
        CurrentUser other = user("customer-2", "CUSTOMER");
        given(ParcelStatus.DELIVERED, other);

        assertThatThrownBy(() -> service.receipt(PARCEL_ID, other)).isInstanceOf(ReceiptAccessDeniedException.class);
    }

    @Test
    @DisplayName("Unknown parcel: the 404 from ParcelQueryService is passed on")
    void unknownParcel() {
        CurrentUser owner = user(OWNER, "CUSTOMER");
        when(parcelQueryService.getAccessibleParcel(eq("missing"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found with id: missing"));

        assertThatThrownBy(() -> service.receipt("missing", owner)).isInstanceOf(ReceiptParcelNotFoundException.class);
    }

    @Test
    @DisplayName("Every status other than DELIVERED gets PARCEL_NOT_DELIVERED and no PDF is built")
    void notDelivered() {
        CurrentUser owner = user(OWNER, "CUSTOMER");
        for (ParcelStatus status : ParcelStatus.values()) {
            if (status == ParcelStatus.DELIVERED) {
                continue;
            }
            given(status, owner);
            assertThatThrownBy(() -> service.receipt(PARCEL_ID, owner))
                    .isInstanceOf(ParcelNotDeliveredException.class)
                    .hasMessageContaining(status.name());
        }
        verify(renderer, never()).render(any());
    }

    @Test
    @DisplayName("A failure while building the PDF is reported as ReceiptGenerationException")
    void generationFailure() {
        CurrentUser owner = user(OWNER, "CUSTOMER");
        given(ParcelStatus.DELIVERED, owner);
        when(renderer.render(any())).thenThrow(new ReceiptGenerationException(new IllegalStateException("boom")));

        assertThatThrownBy(() -> service.receipt(PARCEL_ID, owner)).isInstanceOf(ReceiptGenerationException.class);
    }
}
