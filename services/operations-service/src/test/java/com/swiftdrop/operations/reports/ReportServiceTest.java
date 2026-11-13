package com.swiftdrop.operations.reports;

import com.swiftdrop.common.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private DailyReportRepository dailyReportRepository;

    @Mock
    private ReportProcessedEventRepository processedEventRepository;

    @Mock
    private ReportParcelSequenceRepository sequenceRepository;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(dailyReportRepository, processedEventRepository, sequenceRepository);
    }

    @Test
    void processesSuccessAndFailureUsingColomboDate() {
        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(sequenceRepository.findById("parcel-1")).thenReturn(Optional.empty());
        when(dailyReportRepository.findById(LocalDate.of(2026, 11, 1))).thenReturn(Optional.empty());

        service.processEvent(event("event-1", 1, "DELIVERED", Instant.parse("2026-10-31T19:00:00Z")));
        service.processEvent(event("event-2", 2, "FAILED", Instant.parse("2026-11-01T18:00:00Z")));

        ArgumentCaptor<DailyReportAggregate> aggregate = ArgumentCaptor.forClass(DailyReportAggregate.class);
        verify(dailyReportRepository, times(2)).save(aggregate.capture());
        assertThat(aggregate.getAllValues().get(0).getReportDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(aggregate.getAllValues().get(0).getTotalDeliveries()).isEqualTo(1);
        assertThat(aggregate.getAllValues().get(0).getSuccessfulDeliveries()).isEqualTo(1);
        assertThat(aggregate.getAllValues().get(1).getFailedDeliveries()).isEqualTo(1);
    }

    @Test
    void ignoresDuplicateAndStaleEvents() {
        EventEnvelope duplicate = event("event-1", 2, "DELIVERED", Instant.parse("2026-11-01T10:00:00Z"));
        when(processedEventRepository.existsById("event-1")).thenReturn(true);
        service.processEvent(duplicate);
        verifyNoInteractions(sequenceRepository, dailyReportRepository);

        EventEnvelope stale = event("event-2", 1, "FAILED", Instant.parse("2026-11-01T10:00:00Z"));
        when(processedEventRepository.existsById("event-2")).thenReturn(false);
        when(sequenceRepository.findById("parcel-1"))
                .thenReturn(Optional.of(new ReportParcelSequence("parcel-1", 2)));
        service.processEvent(stale);
        verify(dailyReportRepository, never()).save(any());
        verify(processedEventRepository).save(any(ReportProcessedEvent.class));
    }

    @Test
    void excludesCancellationAndIntermediateEvents() {
        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(sequenceRepository.findById("parcel-1")).thenReturn(Optional.empty());

        service.processEvent(event("event-1", 1, "CANCELLED", Instant.parse("2026-11-01T10:00:00Z")));
        service.processEvent(event("event-2", 2, "IN_TRANSIT", Instant.parse("2026-11-01T10:00:00Z")));

        verify(dailyReportRepository, never()).save(any());
    }

    @Test
    void calculatesZeroSafeSuccessRateAndRejectsInvalidRange() {
        DailyReportAggregate aggregate = new DailyReportAggregate(LocalDate.of(2026, 11, 1));
        when(dailyReportRepository.findByReportDateBetweenOrderByReportDate(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)))
                .thenReturn(java.util.List.of(aggregate));

        assertThat(service.getDailyReport(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)))
                .singleElement()
                .extracting(DailyReportRow::successRate)
                .isEqualTo(0.0);
        assertThatThrownBy(() -> service.getDailyReport(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void returnsEveryInclusiveDateWithZeroRowsForMissingAggregates() {
        DailyReportAggregate aggregate = new DailyReportAggregate(LocalDate.of(2026, 11, 2));
        aggregate.recordSuccess();
        when(dailyReportRepository.findByReportDateBetweenOrderByReportDate(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)))
                .thenReturn(java.util.List.of(aggregate));

        assertThat(service.getDailyReport(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)))
                .extracting(DailyReportRow::reportDate, DailyReportRow::totalDeliveries)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 11, 1), 0),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 11, 2), 1),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 11, 3), 0)
                );
    }

    @Test
    void assignsTerminalEventsToTheCorrectColomboDateAtUtcBoundary() {
        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(sequenceRepository.findById("parcel-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ReportParcelSequence("parcel-1", 1)));
        when(dailyReportRepository.findById(any())).thenReturn(Optional.empty());

        service.processEvent(event("event-before-boundary", 1, "DELIVERED",
                Instant.parse("2026-11-01T18:29:59Z")));
        service.processEvent(event("event-at-boundary", 2, "FAILED",
                Instant.parse("2026-11-01T18:30:00Z")));

        ArgumentCaptor<DailyReportAggregate> aggregate = ArgumentCaptor.forClass(DailyReportAggregate.class);
        verify(dailyReportRepository, times(2)).save(aggregate.capture());
        assertThat(aggregate.getAllValues())
                .extracting(DailyReportAggregate::getReportDate)
                .containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
    }

    @Test
    void acceptsValidParcelCreatedEventWithoutUpdatingDeliveryCounters() {
        when(processedEventRepository.existsById("event-created")).thenReturn(false);
        when(sequenceRepository.findById("parcel-1")).thenReturn(Optional.empty());

        service.processEvent(createdEvent("event-created", "NONE", "CREATED"));

        verify(sequenceRepository).save(any(ReportParcelSequence.class));
        verify(processedEventRepository).save(any(ReportProcessedEvent.class));
        verifyNoInteractions(dailyReportRepository);
    }

    @Test
    void rejectsParcelCreatedEventWithInvalidStatuses() {
        EventEnvelope invalid = createdEvent("event-invalid", "CREATED", "CREATED");

        assertThatThrownBy(() -> service.processEvent(invalid))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processedEventRepository, sequenceRepository, dailyReportRepository);
    }

    @Test
    void rejectsNoneAsTheFromStatusForStatusChangeEvents() {
        EventEnvelope invalid = eventWithFrom(
                "event-invalid", 1, "NONE", "DELIVERED", Instant.parse("2026-11-01T10:00:00Z"));

        assertThatThrownBy(() -> service.processEvent(invalid))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processedEventRepository, sequenceRepository, dailyReportRepository);
    }

    @Test
    void rejectsEventsOutsideTheReportEnvelopeContract() {
        EventEnvelope invalid = new EventEnvelope(
                "event-invalid",
                "PARCEL_ASSIGNED",
                Instant.parse("2026-11-01T10:00:00Z"),
                1L,
                "parcel-1",
                "SD-20261101-1",
                "CREATED",
                "CREATED",
                null,
                null,
                null,
                "customer-1",
                null
        );

        assertThatThrownBy(() -> service.processEvent(invalid))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processedEventRepository, sequenceRepository, dailyReportRepository);
    }

    @Test
    void rejectsNonPositiveSequencesBeforePersistingAnyProjectionState() {
        EventEnvelope invalid = event("event-invalid", 0, "DELIVERED", Instant.parse("2026-11-01T10:00:00Z"));

        assertThatThrownBy(() -> service.processEvent(invalid))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(processedEventRepository, sequenceRepository, dailyReportRepository);
    }

    @Test
    void aggregatesMultipleOutcomesOnSameDayAccurately() {
        LocalDate date = LocalDate.of(2026, 11, 5);
        DailyReportAggregate aggregate = new DailyReportAggregate(date);
        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(sequenceRepository.findById(any())).thenReturn(Optional.empty());
        when(dailyReportRepository.findById(date)).thenReturn(Optional.of(aggregate));

        service.processEvent(eventForParcel("p-1", "e-1", 1, "DELIVERED", Instant.parse("2026-11-05T06:00:00Z")));
        service.processEvent(eventForParcel("p-2", "e-2", 1, "DELIVERED", Instant.parse("2026-11-05T07:00:00Z")));
        service.processEvent(eventForParcel("p-3", "e-3", 1, "DELIVERED", Instant.parse("2026-11-05T08:00:00Z")));
        service.processEvent(eventForParcel("p-4", "e-4", 1, "FAILED", Instant.parse("2026-11-05T09:00:00Z")));

        assertThat(aggregate.getTotalDeliveries()).isEqualTo(4);
        assertThat(aggregate.getSuccessfulDeliveries()).isEqualTo(3);
        assertThat(aggregate.getFailedDeliveries()).isEqualTo(1);

        when(dailyReportRepository.findByReportDateBetweenOrderByReportDate(date, date))
                .thenReturn(java.util.List.of(aggregate));
        DailyReportRow row = service.getDailyReport(date, date).get(0);
        assertThat(row.totalDeliveries()).isEqualTo(4);
        assertThat(row.successfulDeliveries()).isEqualTo(3);
        assertThat(row.failedDeliveries()).isEqualTo(1);
        assertThat(row.successRate()).isEqualTo(0.75);
    }

    @Test
    void preservesLifecycleSequenceWithoutPrematureCounting() {
        when(processedEventRepository.existsById(any())).thenReturn(false);

        Map<String, ReportParcelSequence> sequenceStore = new HashMap<>();
        when(sequenceRepository.findById("parcel-seq-1")).thenAnswer(inv ->
                Optional.ofNullable(sequenceStore.get("parcel-seq-1")));
        when(sequenceRepository.save(any(ReportParcelSequence.class))).thenAnswer(inv -> {
            ReportParcelSequence entity = inv.getArgument(0);
            sequenceStore.put("parcel-seq-1", entity);
            return entity;
        });

        service.processEvent(eventForParcel(
                "parcel-seq-1", "e-step-1", 2, "CREATED", "PICKED_UP", Instant.parse("2026-11-05T04:00:00Z")));
        service.processEvent(eventForParcel(
                "parcel-seq-1", "e-step-2", 3, "PICKED_UP", "IN_TRANSIT", Instant.parse("2026-11-05T05:00:00Z")));
        service.processEvent(eventForParcel(
                "parcel-seq-1", "e-step-3", 4, "IN_TRANSIT", "OUT_FOR_DELIVERY", Instant.parse("2026-11-05T06:00:00Z")));

        verify(dailyReportRepository, never()).save(any());
        assertThat(sequenceStore.get("parcel-seq-1")).isNotNull();
        assertThat(sequenceStore.get("parcel-seq-1").getLastSequence()).isEqualTo(4L);

        LocalDate date = LocalDate.of(2026, 11, 5);
        when(dailyReportRepository.findById(date)).thenReturn(Optional.empty());
        service.processEvent(eventForParcel(
                "parcel-seq-1", "e-step-4", 5, "OUT_FOR_DELIVERY", "DELIVERED", Instant.parse("2026-11-05T07:00:00Z")));

        ArgumentCaptor<DailyReportAggregate> captor = ArgumentCaptor.forClass(DailyReportAggregate.class);
        verify(dailyReportRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTotalDeliveries()).isEqualTo(1);
        assertThat(captor.getValue().getSuccessfulDeliveries()).isEqualTo(1);
        assertThat(captor.getValue().getFailedDeliveries()).isEqualTo(0);
        assertThat(captor.getValue().getReportDate()).isEqualTo(date);
        assertThat(sequenceStore.get("parcel-seq-1").getLastSequence()).isEqualTo(5L);

        // Out-of-order event arriving after sequence advancement does not inflate aggregates or regress sequence
        service.processEvent(eventForParcel(
                "parcel-seq-1", "e-step-out-of-order", 3, "OUT_FOR_DELIVERY", "DELIVERED", Instant.parse("2026-11-05T08:00:00Z")));
        verify(dailyReportRepository, times(1)).save(any());
        assertThat(sequenceStore.get("parcel-seq-1").getLastSequence()).isEqualTo(5L);
    }

    private EventEnvelope eventForParcel(String parcelId, String id, long sequence, String status, Instant occurredAt) {
        return eventForParcel(parcelId, id, sequence, "OUT_FOR_DELIVERY", status, occurredAt);
    }

    private EventEnvelope eventForParcel(
            String parcelId, String id, long sequence, String fromStatus, String toStatus, Instant occurredAt) {
        return new EventEnvelope(
                id,
                "PARCEL_STATUS_CHANGED",
                occurredAt,
                sequence,
                parcelId,
                "SD-20261105-" + parcelId,
                fromStatus,
                toStatus,
                "courier-1",
                null,
                null,
                "customer-1",
                null
        );
    }

    private EventEnvelope createdEvent(String id, String fromStatus, String toStatus) {
        return new EventEnvelope(
                id,
                "PARCEL_CREATED",
                Instant.parse("2026-11-01T10:00:00Z"),
                1L,
                "parcel-1",
                "SD-20261101-1",
                fromStatus,
                toStatus,
                null,
                null,
                null,
                "customer-1",
                null
        );
    }

    private EventEnvelope eventWithFrom(
            String id, long sequence, String fromStatus, String status, Instant occurredAt) {
        return new EventEnvelope(
                id,
                "PARCEL_STATUS_CHANGED",
                occurredAt,
                sequence,
                "parcel-1",
                "SD-20261101-1",
                fromStatus,
                status,
                "courier-1",
                null,
                null,
                "customer-1",
                null
        );
    }

    private EventEnvelope event(String id, long sequence, String status, Instant occurredAt) {
        return new EventEnvelope(
                id,
                "PARCEL_STATUS_CHANGED",
                occurredAt,
                sequence,
                "parcel-1",
                "SD-20261101-1",
                "OUT_FOR_DELIVERY",
                status,
                "courier-1",
                null,
                null,
                "customer-1",
                null
        );
    }
}
