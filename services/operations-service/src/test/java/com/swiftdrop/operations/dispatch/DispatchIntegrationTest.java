package com.swiftdrop.operations.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.operations.dispatch.client.*;
import com.swiftdrop.operations.dispatch.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class DispatchIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private DispatchEventListener dispatchEventListener;

    @Autowired
    private CourierWorkloadRepository courierWorkloadRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private FakeCourierDirectory fakeCourierDirectory;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ParcelServiceClient parcelServiceClient;

    private static final String COURIER_ID = "f8e7d6c5-b4a3-2109-8765-43210fedcba9";
    private static final String PARCEL_ID = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d";

    @BeforeEach
    void cleanUp() {
        processedEventRepository.deleteAll();
        courierWorkloadRepository.deleteAll();
        fakeCourierDirectory.updateAvailability(COURIER_ID, true);
    }

    @Test
    @DisplayName("End-to-end: assign parcel updates workload count through consumed event and reflects on board")
    void assigningParcelUpdatesWorkloadCountThroughConsumedEvent() throws Exception {
        // 1. Setup mock parcel client assignment
        when(parcelServiceClient.assignCourier(eq(PARCEL_ID), eq(COURIER_ID), any()))
            .thenReturn(new ParcelClientResponse(
                PARCEL_ID, "SD-20261028-IT", "CREATED", "cust-1", null, COURIER_ID,
                null, null, 2.0, "SMALL", 500.0, 0, null, "Recipient", "0771234567",
                null, 1L, Instant.now(), Instant.now()
            ));

        // 2. Assign parcel via DispatchService
        AssignmentResult assignment = dispatchService.assignParcel(
            new AssignParcelRequest(PARCEL_ID, COURIER_ID),
            "dispatcher-admin"
        );
        assertThat(assignment.status()).isEqualTo("ASSIGNED");

        // 3. Simulate Kafka domain event emitted by parcel-service: parcel.assigned.v1
        String eventId = UUID.randomUUID().toString();
        EventEnvelope assignedEvent = new EventEnvelope(
            eventId,
            "parcel.assigned.v1",
            Instant.now(),
            1L,
            PARCEL_ID,
            "SD-20261028-IT",
            "CREATED",
            "CREATED",
            COURIER_ID,
            null,
            null,
            "cust-1",
            null
        );

        dispatchEventListener.onEvent(objectMapper.writeValueAsString(assignedEvent));

        // 4. Verify database state in courier_workload
        Optional<CourierWorkloadEntity> workloadOpt = courierWorkloadRepository.findByCourierId(COURIER_ID);
        assertThat(workloadOpt).isPresent();
        assertThat(workloadOpt.get().getActiveCount()).isEqualTo(1);
        assertThat(workloadOpt.get().getLastEventId()).isEqualTo(eventId);

        // 5. Verify processed_event table contains record (idempotency storage)
        assertThat(processedEventRepository.existsByEventId(eventId)).isTrue();

        // 6. Verify workload board reflects the active count
        List<CourierWorkloadItem> board = dispatchService.getCourierWorkloadBoard("ZONE-COLOMBO-NORTH", false);
        CourierWorkloadItem courierItem = board.stream()
            .filter(c -> c.courierId().equals(COURIER_ID))
            .findFirst()
            .orElseThrow();
        assertThat(courierItem.activeCount()).isEqualTo(1);

        // 7. Deliver parcel: status changed to DELIVERED
        String deliveredEventId = UUID.randomUUID().toString();
        EventEnvelope deliveredEvent = new EventEnvelope(
            deliveredEventId,
            "parcel.status-changed.v1",
            Instant.now(),
            2L,
            PARCEL_ID,
            "SD-20261028-IT",
            "IN_TRANSIT",
            "DELIVERED",
            COURIER_ID,
            null,
            null,
            "cust-1",
            null
        );

        dispatchEventListener.onEvent(objectMapper.writeValueAsString(deliveredEvent));

        // 8. Verify count decremented back to 0
        workloadOpt = courierWorkloadRepository.findByCourierId(COURIER_ID);
        assertThat(workloadOpt).isPresent();
        assertThat(workloadOpt.get().getActiveCount()).isEqualTo(0);

        // 9. Verify duplicate event replay does not corrupt count (idempotency)
        dispatchEventListener.onEvent(objectMapper.writeValueAsString(deliveredEvent));
        workloadOpt = courierWorkloadRepository.findByCourierId(COURIER_ID);
        assertThat(workloadOpt).isPresent();
        assertThat(workloadOpt.get().getActiveCount()).isEqualTo(0);
    }
}
