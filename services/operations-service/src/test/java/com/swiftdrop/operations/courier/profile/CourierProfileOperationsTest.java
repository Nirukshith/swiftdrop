package com.swiftdrop.operations.courier.profile;

import com.swiftdrop.operations.courier.execution.DeliveryProofRepository;
import com.swiftdrop.operations.courier.execution.client.ParcelResponse;
import com.swiftdrop.operations.courier.execution.client.ParcelServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierProfileOperationsTest {
    @Mock CourierProfileService profiles;
    @Mock ParcelServiceClient parcels;
    @Mock DeliveryProofRepository proofs;
    @InjectMocks CourierProfileOperations operations;

    @Test
    void failureEndpointRejectsParcelOwnedByAnotherCourier() {
        UUID parcelId = UUID.randomUUID();
        when(parcels.getParcel(parcelId)).thenReturn(new ParcelResponse(
            parcelId, "SD-1", "OUT_FOR_DELIVERY", "customer", null, "other-courier",
            null, null, 1.0, "SMALL", 100.0, 0, null, "Recipient", null, null, null, 1L, null, null
        ));

        assertThatThrownBy(() -> operations.failDelivery("courier-1", parcelId,
            new FailedDeliveryRequest(FailureReasonCode.RECIPIENT_UNAVAILABLE, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void failureEndpointCombinesReasonCodeAndTrimmedNoteWithoutChangingProofSlot() {
        UUID parcelId = UUID.randomUUID();
        when(parcels.getParcel(parcelId)).thenReturn(new ParcelResponse(
            parcelId, "SD-1", "OUT_FOR_DELIVERY", "customer", null, "courier-1",
            null, null, 1.0, "SMALL", 100.0, 0, null, "Recipient", null, null, null, 1L, null, null
        ));

        operations.failDelivery("courier-1", parcelId,
            new FailedDeliveryRequest(FailureReasonCode.OTHER, "  Gate was locked  "));

        var captor = org.mockito.ArgumentCaptor.forClass(com.swiftdrop.operations.courier.execution.client.ParcelStatusUpdateRequest.class);
        verify(parcels).updateStatus(eq(parcelId), captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("OTHER: Gate was locked");
        assertThat(captor.getValue().proof()).isNull();
        assertThat(captor.getValue().idempotencyKey()).isNotBlank();
    }

    @Test
    void failureEndpointRejectsReasonLongerThanParcelLimit() {
        UUID parcelId = UUID.randomUUID();
        when(parcels.getParcel(parcelId)).thenReturn(new ParcelResponse(
            parcelId, "SD-1", "OUT_FOR_DELIVERY", "customer", null, "courier-1",
            null, null, 1.0, "SMALL", 100.0, 0, null, "Recipient", null, null, null, 1L, null, null
        ));

        assertThatThrownBy(() -> operations.failDelivery("courier-1", parcelId,
            new FailedDeliveryRequest(FailureReasonCode.OTHER, "x".repeat(255))))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("255");
    }

    @Test
    void summaryMapsCompletionTimestampOnlyForCompletedStatuses() {
        UUID deliveredId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        when(parcels.getParcelsByCourierAndDate("courier-1", "2026-10-28"))
            .thenReturn(new com.swiftdrop.operations.courier.execution.client.ParcelPageResponse(java.util.List.of(
                new ParcelResponse(deliveredId, "SD-1", "DELIVERED", "customer", null, "courier-1", null, null, 1.0, "SMALL", 100.0, 1, null, "Recipient", null, null, null, 1L, Instant.parse("2026-10-28T10:00:00Z"), Instant.parse("2026-10-28T12:00:00Z")),
                new ParcelResponse(pendingId, "SD-2", "OUT_FOR_DELIVERY", "customer", null, "courier-1", null, null, 1.0, "SMALL", 100.0, 1, null, "Recipient", null, null, null, 1L, Instant.parse("2026-10-28T10:00:00Z"), Instant.parse("2026-10-28T11:00:00Z"))
            ), 2, 1, 0, 2));

        var summary = operations.getSummary("courier-1", java.time.LocalDate.parse("2026-10-28"));

        assertThat(summary.deliveries()).extracting("completedAt")
            .containsExactly(Instant.parse("2026-10-28T12:00:00Z"), null);
    }
}
