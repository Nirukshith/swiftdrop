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
