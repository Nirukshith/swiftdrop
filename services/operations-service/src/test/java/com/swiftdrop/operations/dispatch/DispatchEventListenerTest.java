package com.swiftdrop.operations.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchEventListenerTest {

    private WorkloadCounter workloadCounter;

    @Mock
    private CourierWorkloadRepository courierWorkloadRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private ObjectMapper objectMapper;
    private DispatchEventListener listener;

    private static final String COURIER_1 = "courier-1111";
    private static final String COURIER_2 = "courier-2222";
    private static final String PARCEL_ID = "parcel-9999";

    @BeforeEach
    void setUp() {
        workloadCounter = new WorkloadCounter();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        listener = new DispatchEventListener(
            workloadCounter,
            courierWorkloadRepository,
            processedEventRepository,
            objectMapper
        );
    }

    @Test
    @DisplayName("Increments courier active count on parcel.assigned.v1 initial assignment")
    void onEvent_initialAssignment_incrementsCount() {
        EventEnvelope event = new EventEnvelope(
            "evt-001",
            "parcel.assigned.v1",
            Instant.now(),
            1L,
            PARCEL_ID,
            "SD-123",
            "CREATED",
            "CREATED",
            COURIER_1,
            null,
            null,
            "cust-1",
            null
        );

        when(processedEventRepository.existsByEventId("evt-001")).thenReturn(false);
        when(courierWorkloadRepository.findByCourierId(COURIER_1)).thenReturn(Optional.empty());

        listener.processEvent(event);

        ArgumentCaptor<CourierWorkloadEntity> workloadCaptor = ArgumentCaptor.forClass(CourierWorkloadEntity.class);
        verify(courierWorkloadRepository).save(workloadCaptor.capture());
        assertThat(workloadCaptor.getValue().getCourierId()).isEqualTo(COURIER_1);
        assertThat(workloadCaptor.getValue().getActiveCount()).isEqualTo(1);
        assertThat(workloadCaptor.getValue().getLastSequence()).isEqualTo(1L);

        ArgumentCaptor<ProcessedEventEntity> eventCaptor = ArgumentCaptor.forClass(ProcessedEventEntity.class);
        verify(processedEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo("evt-001");
        assertThat(workloadCounter.getActiveCount(COURIER_1)).isEqualTo(1);
    }

    @Test
    @DisplayName("Decrements previous courier and increments new courier on reassignment")
    void onEvent_reassignment_updatesBothCouriers() {
        EventEnvelope event = new EventEnvelope(
            "evt-002",
            "parcel.assigned.v1",
            Instant.now(),
            2L,
            PARCEL_ID,
            "SD-123",
            "CREATED",
            "CREATED",
            COURIER_2,
            COURIER_1,
            null,
            "cust-1",
            "Reassigned"
        );

        when(processedEventRepository.existsByEventId("evt-002")).thenReturn(false);
        when(courierWorkloadRepository.findByCourierId(COURIER_1))
            .thenReturn(Optional.of(new CourierWorkloadEntity(COURIER_1, 3, 1L, "evt-001")));
        when(courierWorkloadRepository.findByCourierId(COURIER_2))
            .thenReturn(Optional.of(new CourierWorkloadEntity(COURIER_2, 0, 0L, null)));

        listener.processEvent(event);

        ArgumentCaptor<CourierWorkloadEntity> workloadCaptor = ArgumentCaptor.forClass(CourierWorkloadEntity.class);
        verify(courierWorkloadRepository, times(2)).save(workloadCaptor.capture());

        CourierWorkloadEntity savedCourier1 = workloadCaptor.getAllValues().get(0);
        CourierWorkloadEntity savedCourier2 = workloadCaptor.getAllValues().get(1);

        assertThat(savedCourier1.getCourierId()).isEqualTo(COURIER_1);
        assertThat(savedCourier1.getActiveCount()).isEqualTo(2);

        assertThat(savedCourier2.getCourierId()).isEqualTo(COURIER_2);
        assertThat(savedCourier2.getActiveCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Decrements courier count on terminal DELIVERED event")
    void onEvent_delivered_decrementsCount() {
        EventEnvelope event = new EventEnvelope(
            "evt-003",
            "parcel.status-changed.v1",
            Instant.now(),
            3L,
            PARCEL_ID,
            "SD-123",
            "IN_TRANSIT",
            "DELIVERED",
            COURIER_1,
            null,
            null,
            "cust-1",
            null
        );

        when(processedEventRepository.existsByEventId("evt-003")).thenReturn(false);
        when(courierWorkloadRepository.findByCourierId(COURIER_1))
            .thenReturn(Optional.of(new CourierWorkloadEntity(COURIER_1, 2, 2L, "evt-002")));

        listener.processEvent(event);

        ArgumentCaptor<CourierWorkloadEntity> workloadCaptor = ArgumentCaptor.forClass(CourierWorkloadEntity.class);
        verify(courierWorkloadRepository).save(workloadCaptor.capture());
        assertThat(workloadCaptor.getValue().getActiveCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Decrements courier count on terminal FAILED event")
    void onEvent_failed_decrementsCount() {
        EventEnvelope event = new EventEnvelope(
            "evt-004",
            "parcel.status-changed.v1",
            Instant.now(),
            4L,
            PARCEL_ID,
            "SD-123",
            "IN_TRANSIT",
            "FAILED",
            COURIER_1,
            null,
            null,
            "cust-1",
            "Recipient absent"
        );

        when(processedEventRepository.existsByEventId("evt-004")).thenReturn(false);
        when(courierWorkloadRepository.findByCourierId(COURIER_1))
            .thenReturn(Optional.of(new CourierWorkloadEntity(COURIER_1, 1, 3L, "evt-003")));

        listener.processEvent(event);

        ArgumentCaptor<CourierWorkloadEntity> workloadCaptor = ArgumentCaptor.forClass(CourierWorkloadEntity.class);
        verify(courierWorkloadRepository).save(workloadCaptor.capture());
        assertThat(workloadCaptor.getValue().getActiveCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Skips duplicate events when eventId already in processed_event table")
    void onEvent_duplicateEventId_isSkipped() {
        EventEnvelope event = new EventEnvelope(
            "evt-duplicate",
            "parcel.assigned.v1",
            Instant.now(),
            1L,
            PARCEL_ID,
            "SD-123",
            "CREATED",
            "CREATED",
            COURIER_1,
            null,
            null,
            "cust-1",
            null
        );

        when(processedEventRepository.existsByEventId("evt-duplicate")).thenReturn(true);

        listener.processEvent(event);

        verify(courierWorkloadRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }
}
