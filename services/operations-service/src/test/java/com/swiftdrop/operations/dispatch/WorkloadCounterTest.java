package com.swiftdrop.operations.dispatch;

import com.swiftdrop.common.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkloadCounter Pure-Java Domain Tests")
class WorkloadCounterTest {

    private WorkloadCounter workloadCounter;

    @BeforeEach
    void setUp() {
        workloadCounter = new WorkloadCounter();
    }

    private EventEnvelope createEnvelope(
            String eventType,
            Long sequence,
            String parcelId,
            String fromStatus,
            String toStatus,
            String courierId,
            String previousCourierId
    ) {
        return new EventEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                sequence,
                parcelId,
                "SD-20261025-000001",
                fromStatus,
                toStatus,
                courierId,
                previousCourierId,
                "merchant-1",
                "customer-1",
                null
        );
    }

    @Test
    @DisplayName("Assign parcel: increments courier active workload count")
    void testAssignParcel() {
        String courierA = "courier-A";
        String parcel1 = "parcel-1";

        assertEquals(0, workloadCounter.getActiveCount(courierA));

        EventEnvelope assignEvent = createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_ASSIGNED,
                1L,
                parcel1,
                "CREATED",
                "CREATED",
                courierA,
                null
        );

        boolean applied = workloadCounter.apply(assignEvent);

        assertTrue(applied);
        assertEquals(1, workloadCounter.getActiveCount(courierA));
    }

    @Test
    @DisplayName("Deliver parcel: decrements courier active workload count")
    void testDeliverParcel() {
        String courierA = "courier-A";
        String parcel1 = "parcel-1";

        // Assign parcel (seq 1)
        workloadCounter.apply(createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_ASSIGNED,
                1L,
                parcel1,
                "CREATED",
                "CREATED",
                courierA,
                null
        ));
        assertEquals(1, workloadCounter.getActiveCount(courierA));

        // Deliver parcel (seq 2)
        EventEnvelope deliverEvent = createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_STATUS_CHANGED,
                2L,
                parcel1,
                "OUT_FOR_DELIVERY",
                WorkloadCounter.STATUS_DELIVERED,
                courierA,
                null
        );

        boolean applied = workloadCounter.apply(deliverEvent);

        assertTrue(applied);
        assertEquals(0, workloadCounter.getActiveCount(courierA));
    }

    @Test
    @DisplayName("Fail parcel: decrements courier active workload count")
    void testFailParcel() {
        String courierA = "courier-A";
        String parcel1 = "parcel-1";

        // Assign parcel (seq 1)
        workloadCounter.apply(createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_ASSIGNED,
                1L,
                parcel1,
                "CREATED",
                "CREATED",
                courierA,
                null
        ));
        assertEquals(1, workloadCounter.getActiveCount(courierA));

        // Mark failed (seq 2)
        EventEnvelope failEvent = createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_STATUS_CHANGED,
                2L,
                parcel1,
                "OUT_FOR_DELIVERY",
                WorkloadCounter.STATUS_FAILED,
                courierA,
                null
        );

        boolean applied = workloadCounter.apply(failEvent);

        assertTrue(applied);
        assertEquals(0, workloadCounter.getActiveCount(courierA));
    }

    @Test
    @DisplayName("Cancel an assigned parcel: decrements courier active workload count")
    void testCancelAssignedParcel() {
        String courierA = "courier-A";
        String parcel1 = "parcel-1";

        // Assign parcel (seq 1)
        workloadCounter.apply(createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_ASSIGNED,
                1L,
                parcel1,
                "CREATED",
                "CREATED",
                courierA,
                null
        ));
        assertEquals(1, workloadCounter.getActiveCount(courierA));

        // Cancel parcel (seq 2)
        EventEnvelope cancelEvent = createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_STATUS_CHANGED,
                2L,
                parcel1,
                "CREATED",
                WorkloadCounter.STATUS_CANCELLED,
                courierA,
                null
        );

        boolean applied = workloadCounter.apply(cancelEvent);

        assertTrue(applied);
        assertEquals(0, workloadCounter.getActiveCount(courierA));
    }

    @Test
    @DisplayName("Reassign parcel from A to B: decrements courier A and increments courier B")
    void testReassignParcelAtoB() {
        String courierA = "courier-A";
        String courierB = "courier-B";
        String parcel1 = "parcel-1";

        // Assign initially to courier A (seq 1)
        workloadCounter.apply(createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_ASSIGNED,
                1L,
                parcel1,
                "CREATED",
                "CREATED",
                courierA,
                null
        ));
        assertEquals(1, workloadCounter.getActiveCount(courierA));
        assertEquals(0, workloadCounter.getActiveCount(courierB));

        // Reassign from courier A to courier B (seq 2)
        EventEnvelope reassignEvent = createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_ASSIGNED,
                2L,
                parcel1,
                "CREATED",
                "CREATED",
                courierB,
                courierA // previousCourierId
        );

        boolean applied = workloadCounter.apply(reassignEvent);

        assertTrue(applied);
        assertEquals(0, workloadCounter.getActiveCount(courierA));
        assertEquals(1, workloadCounter.getActiveCount(courierB));
    }

    @Test
    @DisplayName("Duplicate event: idempotent consumption ignores repeated eventId")
    void testDuplicateEventIdempotency() {
        String courierA = "courier-A";
        String parcel1 = "parcel-1";

        EventEnvelope assignEvent = createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_ASSIGNED,
                1L,
                parcel1,
                "CREATED",
                "CREATED",
                courierA,
                null
        );

        // First application
        assertTrue(workloadCounter.apply(assignEvent));
        assertEquals(1, workloadCounter.getActiveCount(courierA));
        assertTrue(workloadCounter.isProcessed(assignEvent.eventId()));

        // Repeated application of exact same event
        assertFalse(workloadCounter.apply(assignEvent));
        assertEquals(1, workloadCounter.getActiveCount(courierA));
    }

    @Test
    @DisplayName("Out-of-order event: older sequence arriving after newer sequence is ignored")
    void testOutOfOrderEventIgnored() {
        String courierA = "courier-A";
        String parcel1 = "parcel-1";

        // Step 1: Deliver event with sequence 5 arrives first
        EventEnvelope deliverEvent = createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_STATUS_CHANGED,
                5L,
                parcel1,
                "OUT_FOR_DELIVERY",
                WorkloadCounter.STATUS_DELIVERED,
                courierA,
                null
        );
        workloadCounter.apply(deliverEvent);
        assertEquals(0, workloadCounter.getActiveCount(courierA));

        // Step 2: Delayed assignment event with sequence 1 arrives later
        EventEnvelope delayedAssignEvent = createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_ASSIGNED,
                1L,
                parcel1,
                "CREATED",
                "CREATED",
                courierA,
                null
        );

        // Should be rejected because seq 1 <= seq 5 for parcel1
        boolean applied = workloadCounter.apply(delayedAssignEvent);

        assertFalse(applied);
        assertEquals(0, workloadCounter.getActiveCount(courierA), "Stale assignment must not increment count");
    }

    @Test
    @DisplayName("Count lower bound: active count never drops below 0")
    void testCountNeverNegative() {
        String courierA = "courier-A";
        String parcel1 = "parcel-1";

        // Terminal event for a courier with 0 active count
        EventEnvelope deliverEvent = createEnvelope(
                WorkloadCounter.TOPIC_PARCEL_STATUS_CHANGED,
                1L,
                parcel1,
                "OUT_FOR_DELIVERY",
                WorkloadCounter.STATUS_DELIVERED,
                courierA,
                null
        );

        workloadCounter.apply(deliverEvent);
        assertEquals(0, workloadCounter.getActiveCount(courierA));
    }
}
