package com.swiftdrop.operations.dispatch;

import com.swiftdrop.operations.dispatch.client.*;
import com.swiftdrop.operations.dispatch.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private ParcelServiceClient parcelServiceClient;

    @Mock
    private CourierDirectory courierDirectory;

    @Mock
    private CourierWorkloadRepository courierWorkloadRepository;

    @InjectMocks
    private DispatchService dispatchService;

    private static final String COURIER_ID = "f8e7d6c5-b4a3-2109-8765-43210fedcba9";
    private static final String PARCEL_ID = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d";

    private CourierSummary sampleCourier(boolean available) {
        return new CourierSummary(
            COURIER_ID,
            "Saman Perera",
            "+94771234567",
            "ZONE-COLOMBO-NORTH",
            available,
            "MOTORCYCLE"
        );
    }

    @Test
    @DisplayName("listUnassignedParcels returns mapped page summary")
    void listUnassignedParcels_returnsMappedSummaries() {
        AddressClientDto pickup = new AddressClientDto("10 Main St", null, "Colombo", "Colombo", "00100");
        AddressClientDto dropoff = new AddressClientDto("20 Hill St", null, "Kandy", "Kandy", "20000");

        ParcelClientResponse parcel = new ParcelClientResponse(
            PARCEL_ID,
            "SD-20261028-001",
            "CREATED",
            "cust-1",
            null,
            null,
            pickup,
            dropoff,
            2.5,
            "MEDIUM",
            750.0,
            0,
            null,
            "Recipient One",
            "+94771111111",
            "Handle with care",
            1L,
            Instant.now(),
            Instant.now()
        );

        ParcelPageClientResponse pageResponse = new ParcelPageClientResponse(List.of(parcel), 1, 1, 0, 20);
        when(parcelServiceClient.queryUnassignedParcels(eq("ZONE-COLOMBO-NORTH"), any(), eq(0), eq(20)))
            .thenReturn(pageResponse);

        UnassignedParcelsPage result = dispatchService.listUnassignedParcels("ZONE-COLOMBO-NORTH", null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).parcelId()).isEqualTo(PARCEL_ID);
        assertThat(result.content().get(0).trackingNumber()).isEqualTo("SD-20261028-001");
        assertThat(result.content().get(0).pickupZone()).isEqualTo("Colombo");
    }

    @Test
    @DisplayName("assignParcel assigns successfully when courier is available")
    void assignParcel_success() {
        when(courierDirectory.findCourierById(COURIER_ID)).thenReturn(Optional.of(sampleCourier(true)));
        when(parcelServiceClient.assignCourier(eq(PARCEL_ID), eq(COURIER_ID), eq("admin-user")))
            .thenReturn(mock(ParcelClientResponse.class));

        AssignmentResult result = dispatchService.assignParcel(
            new AssignParcelRequest(PARCEL_ID, COURIER_ID),
            "admin-user"
        );

        assertThat(result.parcelId()).isEqualTo(PARCEL_ID);
        assertThat(result.courierId()).isEqualTo(COURIER_ID);
        assertThat(result.status()).isEqualTo("ASSIGNED");
        verify(parcelServiceClient).assignCourier(PARCEL_ID, COURIER_ID, "admin-user");
    }

    @Test
    @DisplayName("assignParcel throws CourierUnavailableException when courier is offline")
    void assignParcel_courierUnavailable() {
        when(courierDirectory.findCourierById(COURIER_ID)).thenReturn(Optional.of(sampleCourier(false)));

        assertThatThrownBy(() -> dispatchService.assignParcel(
            new AssignParcelRequest(PARCEL_ID, COURIER_ID),
            "dispatcher-1"
        )).isInstanceOf(CourierUnavailableException.class)
          .hasMessageContaining("unavailable");

        verifyNoInteractions(parcelServiceClient);
    }

    @Test
    @DisplayName("assignParcel throws CourierNotFoundException when courier does not exist")
    void assignParcel_courierNotFound() {
        when(courierDirectory.findCourierById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatchService.assignParcel(
            new AssignParcelRequest(PARCEL_ID, "unknown-id"),
            "dispatcher-1"
        )).isInstanceOf(CourierNotFoundException.class);

        verifyNoInteractions(parcelServiceClient);
    }

    @Test
    @DisplayName("assignParcel propagates ParcelAlreadyAssignedException on conflict")
    void assignParcel_alreadyAssigned() {
        when(courierDirectory.findCourierById(COURIER_ID)).thenReturn(Optional.of(sampleCourier(true)));
        when(parcelServiceClient.assignCourier(PARCEL_ID, COURIER_ID, "dispatcher-1"))
            .thenThrow(new ParcelAlreadyAssignedException("Parcel already assigned"));

        assertThatThrownBy(() -> dispatchService.assignParcel(
            new AssignParcelRequest(PARCEL_ID, COURIER_ID),
            "dispatcher-1"
        )).isInstanceOf(ParcelAlreadyAssignedException.class);
    }

    @Test
    @DisplayName("getCourierWorkloadBoard merges directory with workload counts")
    void getCourierWorkloadBoard_mergesWorkloadCounts() {
        when(courierDirectory.listAllCouriers()).thenReturn(List.of(sampleCourier(true)));
        when(courierWorkloadRepository.findByCourierId(COURIER_ID))
            .thenReturn(Optional.of(new CourierWorkloadEntity(COURIER_ID, 3, 10L, "evt-1")));

        List<CourierWorkloadItem> board = dispatchService.getCourierWorkloadBoard(null, false);

        assertThat(board).hasSize(1);
        assertThat(board.get(0).courierId()).isEqualTo(COURIER_ID);
        assertThat(board.get(0).activeCount()).isEqualTo(3);
        assertThat(board.get(0).available()).isTrue();
    }

    @Test
    @DisplayName("updateCourierAvailability toggles availability and returns updated workload item")
    void updateCourierAvailability_updatesState() {
        when(courierDirectory.findCourierById(COURIER_ID)).thenReturn(Optional.of(sampleCourier(true)));
        when(courierWorkloadRepository.findByCourierId(COURIER_ID))
            .thenReturn(Optional.of(new CourierWorkloadEntity(COURIER_ID, 2, 5L, "evt-0")));

        CourierWorkloadItem item = dispatchService.updateCourierAvailability(COURIER_ID, false);

        verify(courierDirectory).updateAvailability(COURIER_ID, false);
        assertThat(item.courierId()).isEqualTo(COURIER_ID);
        assertThat(item.activeCount()).isEqualTo(2);
    }
}
