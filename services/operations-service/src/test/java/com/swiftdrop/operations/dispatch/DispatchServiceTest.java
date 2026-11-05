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

    @Test
    @DisplayName("listFailedParcels returns mapped failed page summary")
    void listFailedParcels_returnsMappedSummaries() {
        AddressClientDto pickup = new AddressClientDto("10 Main St", null, "Colombo", "Colombo", "00100");
        AddressClientDto dropoff = new AddressClientDto("20 Hill St", null, "Kandy", "Kandy", "20000");

        ParcelClientResponse parcel = new ParcelClientResponse(
            PARCEL_ID,
            "SD-20261028-002",
            "FAILED",
            "cust-1",
            null,
            null,
            pickup,
            dropoff,
            2.5,
            "MEDIUM",
            750.0,
            1,
            "Customer not home",
            "Recipient Two",
            "+94772222222",
            "Handle with care",
            2L,
            Instant.now(),
            Instant.now()
        );

        ParcelPageClientResponse pageResponse = new ParcelPageClientResponse(List.of(parcel), 1, 1, 0, 20);
        when(parcelServiceClient.queryFailedParcels(eq("ZONE-COLOMBO-NORTH"), any(), eq(0), eq(20)))
            .thenReturn(pageResponse);

        FailedParcelsPage result = dispatchService.listFailedParcels("ZONE-COLOMBO-NORTH", null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).parcelId()).isEqualTo(PARCEL_ID);
        assertThat(result.content().get(0).status()).isEqualTo("FAILED");
        assertThat(result.content().get(0).attemptCount()).isEqualTo(1);
        assertThat(result.content().get(0).failureReason()).isEqualTo("Customer not home");
    }

    @Test
    @DisplayName("requeueParcel calls parcelServiceClient and returns RequeueResponse")
    void requeueParcel_success() {
        Instant now = Instant.now();
        ParcelClientResponse response = new ParcelClientResponse(
            PARCEL_ID,
            "SD-20261028-002",
            "CREATED",
            "cust-1",
            null,
            null,
            null,
            null,
            2.5,
            "MEDIUM",
            750.0,
            2,
            null,
            "Recipient Two",
            "+94772222222",
            null,
            3L,
            now.minusSeconds(3600),
            now
        );

        when(parcelServiceClient.requeueParcel(eq(PARCEL_ID), eq("Retry delivery"), eq("dispatcher-1")))
            .thenReturn(response);

        RequeueResponse result = dispatchService.requeueParcel(PARCEL_ID, "Retry delivery", "dispatcher-1");

        assertThat(result.parcelId()).isEqualTo(PARCEL_ID);
        assertThat(result.status()).isEqualTo("CREATED");
        assertThat(result.attemptCount()).isEqualTo(2);
        assertThat(result.requeuedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("bulkAssignParcels handles partial success and duplicate batch items")
    void bulkAssignParcels_partialSuccessAndBatchValidation() {
        String p1 = "parcel-1";
        String p2 = "parcel-2";
        String p3 = "parcel-3";
        String p4 = "parcel-4";
        String offlineCourier = "courier-offline";
        String missingCourier = "courier-missing";

        when(courierDirectory.findCourierById(COURIER_ID)).thenReturn(Optional.of(sampleCourier(true)));
        when(courierDirectory.findCourierById(offlineCourier)).thenReturn(Optional.of(new CourierSummary(
            offlineCourier, "Offline Courier", "+94770000000", "ZONE-COLOMBO", false, "VAN"
        )));
        when(courierDirectory.findCourierById(missingCourier)).thenReturn(Optional.empty());

        // p1 succeeds
        when(parcelServiceClient.assignCourier(p1, COURIER_ID, "dispatcher-1"))
            .thenReturn(mock(ParcelClientResponse.class));
        // p4 parcel already assigned
        when(parcelServiceClient.assignCourier(p4, COURIER_ID, "dispatcher-1"))
            .thenThrow(new ParcelAlreadyAssignedException("Parcel p4 already assigned"));

        BulkAssignRequest request = new BulkAssignRequest(List.of(
            new AssignParcelRequest(p1, COURIER_ID),         // 1. Success
            new AssignParcelRequest(p2, offlineCourier),     // 2. Fail: Courier unavailable
            new AssignParcelRequest(p3, missingCourier),     // 3. Fail: Courier not found
            new AssignParcelRequest(p4, COURIER_ID),         // 4. Fail: Parcel already assigned
            new AssignParcelRequest(p1, COURIER_ID)          // 5. Fail: Duplicate in batch
        ));

        BulkAssignResponse result = dispatchService.bulkAssignParcels(request, "dispatcher-1");

        assertThat(result.totalRequested()).isEqualTo(5);
        assertThat(result.successfulCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(4);

        assertThat(result.results().get(0).success()).isTrue();
        assertThat(result.results().get(0).parcelId()).isEqualTo(p1);

        assertThat(result.results().get(1).success()).isFalse();
        assertThat(result.results().get(1).errorCode()).isEqualTo("COURIER_UNAVAILABLE");

        assertThat(result.results().get(2).success()).isFalse();
        assertThat(result.results().get(2).errorCode()).isEqualTo("COURIER_NOT_FOUND");

        assertThat(result.results().get(3).success()).isFalse();
        assertThat(result.results().get(3).errorCode()).isEqualTo("PARCEL_ALREADY_ASSIGNED");

        assertThat(result.results().get(4).success()).isFalse();
        assertThat(result.results().get(4).errorCode()).isEqualTo("DUPLICATE_PARCEL_IN_BATCH");
    }
}
