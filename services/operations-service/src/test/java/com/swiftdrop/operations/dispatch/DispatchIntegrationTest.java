package com.swiftdrop.operations.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.operations.courier.profile.CourierProfileEntity;
import com.swiftdrop.operations.courier.profile.CourierProfileRepository;
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
    private ParcelSequenceRepository parcelSequenceRepository;

    @Autowired
    private CourierProfileRepository courierProfileRepository;

    @Autowired
    private WorkloadCounter workloadCounter;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ParcelServiceClient parcelServiceClient;

    private static final String COURIER_ID = "f8e7d6c5-b4a3-2109-8765-43210fedcba9";
    private static final String COURIER_B_ID = "a2b3c4d5-e6f7-4819-9012-34567890abcd";
    private static final String PARCEL_ID = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d";

    @BeforeEach
    void cleanUp() {
        workloadCounter.reset();
        processedEventRepository.deleteAll();
        parcelSequenceRepository.deleteAll();
        courierWorkloadRepository.deleteAll();
        courierProfileRepository.deleteAll();

        courierProfileRepository.save(CourierProfileEntity.builder()
            .userId(COURIER_ID)
            .name("Saman Perera")
            .phone("+94771234567")
            .zone("ZONE-COLOMBO-NORTH")
            .availability(true)
            .vehicleType("MOTORCYCLE")
            .build());

        courierProfileRepository.save(CourierProfileEntity.builder()
            .userId(COURIER_B_ID)
            .name("Kamal Silva")
            .phone("+94772345678")
            .zone("ZONE-COLOMBO-NORTH")
            .availability(true)
            .vehicleType("VAN")
            .build());
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

        // 2. Assign parcel via DispatchService (authoritative workload update does NOT happen here)
        AssignmentResult assignment = dispatchService.assignParcel(
            new AssignParcelRequest(PARCEL_ID, COURIER_ID),
            "dispatcher-admin"
        );
        assertThat(assignment.status()).isEqualTo("ASSIGNED");

        // 3. Simulate Kafka domain event emitted by parcel-service: parcel.assigned.v1 (initial assignment)
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

        // 5. Verify processed_event and parcel_sequence tables contain records
        assertThat(processedEventRepository.existsByEventId(eventId)).isTrue();
        assertThat(parcelSequenceRepository.findById(PARCEL_ID)).isPresent();
        assertThat(parcelSequenceRepository.findById(PARCEL_ID).get().getLastSequence()).isEqualTo(1L);

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

    @Test
    @DisplayName("End-to-end: reassignment from Courier A to Courier B decrements A and increments B")
    void reassignmentUpdatesBothCouriersWorkloadsCorrectly() throws Exception {
        // 1. Initial assignment to Courier A (seq 1)
        String event1Id = UUID.randomUUID().toString();
        EventEnvelope assignEvent = new EventEnvelope(
            event1Id,
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
        dispatchEventListener.onEvent(objectMapper.writeValueAsString(assignEvent));

        assertThat(courierWorkloadRepository.findByCourierId(COURIER_ID).get().getActiveCount()).isEqualTo(1);
        assertThat(courierWorkloadRepository.findByCourierId(COURIER_B_ID).map(CourierWorkloadEntity::getActiveCount).orElse(0)).isEqualTo(0);

        // 2. Reassignment to Courier B (seq 2, previousCourierId = Courier A)
        String event2Id = UUID.randomUUID().toString();
        EventEnvelope reassignEvent = new EventEnvelope(
            event2Id,
            "parcel.assigned.v1",
            Instant.now(),
            2L,
            PARCEL_ID,
            "SD-20261028-IT",
            "CREATED",
            "CREATED",
            COURIER_B_ID,
            COURIER_ID,
            null,
            "cust-1",
            "Reassigned by dispatcher"
        );
        dispatchEventListener.onEvent(objectMapper.writeValueAsString(reassignEvent));

        // 3. Verify Courier A decremented to 0, Courier B incremented to 1
        assertThat(courierWorkloadRepository.findByCourierId(COURIER_ID).get().getActiveCount()).isEqualTo(0);
        assertThat(courierWorkloadRepository.findByCourierId(COURIER_B_ID).get().getActiveCount()).isEqualTo(1);
        assertThat(parcelSequenceRepository.findById(PARCEL_ID).get().getLastSequence()).isEqualTo(2L);

        // 4. Same-courier reassignment (seq 3, previousCourierId = B, courierId = B) does not change counts
        String event3Id = UUID.randomUUID().toString();
        EventEnvelope sameCourierEvent = new EventEnvelope(
            event3Id,
            "parcel.assigned.v1",
            Instant.now(),
            3L,
            PARCEL_ID,
            "SD-20261028-IT",
            "CREATED",
            "CREATED",
            COURIER_B_ID,
            COURIER_B_ID,
            null,
            "cust-1",
            "Same courier reassignment"
        );
        dispatchEventListener.onEvent(objectMapper.writeValueAsString(sameCourierEvent));
        assertThat(courierWorkloadRepository.findByCourierId(COURIER_B_ID).get().getActiveCount()).isEqualTo(1);

        // 5. Stale sequence rejection after application restart simulation
        // Reset in-memory WorkloadCounter to simulate restart
        workloadCounter.reset();

        // Stale event with sequence 1 arriving now
        String staleEventId = UUID.randomUUID().toString();
        EventEnvelope staleEvent = new EventEnvelope(
            staleEventId,
            "parcel.assigned.v1",
            Instant.now(),
            1L, // stale: sequence in DB is 3L
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
        dispatchEventListener.onEvent(objectMapper.writeValueAsString(staleEvent));

        // Stale event was rejected by database sequence check: Courier A is still 0, Courier B is still 1
        assertThat(courierWorkloadRepository.findByCourierId(COURIER_ID).get().getActiveCount()).isEqualTo(0);
        assertThat(courierWorkloadRepository.findByCourierId(COURIER_B_ID).get().getActiveCount()).isEqualTo(1);
    }
}
