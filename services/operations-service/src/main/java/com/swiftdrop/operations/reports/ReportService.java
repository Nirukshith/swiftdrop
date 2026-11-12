package com.swiftdrop.operations.reports;

import com.swiftdrop.common.EventEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {

    static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Colombo");
    private static final String STATUS_CHANGED = "PARCEL_STATUS_CHANGED";
    private static final String PARCEL_CREATED = "PARCEL_CREATED";
    private static final String DELIVERED = "DELIVERED";
    private static final String FAILED = "FAILED";
    private static final Set<String> ACCEPTED_EVENT_TYPES = Set.of(PARCEL_CREATED, STATUS_CHANGED);
    private static final Set<String> STATUS_VALUES = Set.of(
            "CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED", "CANCELLED");

    private final DailyReportRepository dailyReportRepository;
    private final ReportProcessedEventRepository processedEventRepository;
    private final ReportParcelSequenceRepository sequenceRepository;

    public ReportService(
            DailyReportRepository dailyReportRepository,
            ReportProcessedEventRepository processedEventRepository,
            ReportParcelSequenceRepository sequenceRepository
    ) {
        this.dailyReportRepository = dailyReportRepository;
        this.processedEventRepository = processedEventRepository;
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional(readOnly = true)
    public List<DailyReportRow> getDailyReport(LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<LocalDate, DailyReportAggregate> aggregates = dailyReportRepository
                .findByReportDateBetweenOrderByReportDate(from, to)
                .stream()
                .collect(Collectors.toMap(DailyReportAggregate::getReportDate, Function.identity()));

        return from.datesUntil(to.plusDays(1))
                .map(reportDate -> toRow(reportDate, aggregates.get(reportDate)))
                .toList();
    }

    private DailyReportRow toRow(LocalDate reportDate, DailyReportAggregate aggregate) {
        if (aggregate == null) {
            return new DailyReportRow(reportDate, 0, 0, 0, 0.0);
        }

        int total = aggregate.getTotalDeliveries();
        double successRate = total == 0 ? 0.0 : (double) aggregate.getSuccessfulDeliveries() / total;
        return new DailyReportRow(
                reportDate,
                total,
                aggregate.getSuccessfulDeliveries(),
                aggregate.getFailedDeliveries(),
                successRate
        );
    }

    @Transactional
    public void processEvent(EventEnvelope event) {
        validateEvent(event);

        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }

        ReportParcelSequence sequence = sequenceRepository.findById(event.parcelId()).orElse(null);
        if (sequence != null && event.sequence() <= sequence.getLastSequence()) {
            processedEventRepository.save(new ReportProcessedEvent(
                    event.eventId(), event.eventType(), event.parcelId()));
            return;
        }

        if (sequence == null) {
            sequenceRepository.save(new ReportParcelSequence(event.parcelId(), event.sequence()));
        } else {
            sequence.setLastSequence(event.sequence());
            sequenceRepository.save(sequence);
        }

        if (STATUS_CHANGED.equals(event.eventType())) {
            LocalDate reportDate = event.occurredAt().atZone(REPORT_ZONE).toLocalDate();
            if (DELIVERED.equals(event.toStatus()) || FAILED.equals(event.toStatus())) {
                DailyReportAggregate aggregate = dailyReportRepository.findById(reportDate)
                        .orElseGet(() -> new DailyReportAggregate(reportDate));
                if (DELIVERED.equals(event.toStatus())) {
                    aggregate.recordSuccess();
                } else {
                    aggregate.recordFailure();
                }
                dailyReportRepository.save(aggregate);
            }
        }

        processedEventRepository.save(new ReportProcessedEvent(
                event.eventId(), event.eventType(), event.parcelId()));
    }

    private void validateEvent(EventEnvelope event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()
                || event.parcelId() == null || event.parcelId().isBlank()
                || event.trackingNumber() == null || event.trackingNumber().isBlank()
                || event.eventType() == null || !ACCEPTED_EVENT_TYPES.contains(event.eventType())
                || event.occurredAt() == null || event.sequence() == null || event.sequence() < 1
                || !hasValidStatuses(event)) {
            throw new IllegalArgumentException("Reporting event is missing required envelope fields");
        }
    }

    private boolean hasValidStatuses(EventEnvelope event) {
        if (PARCEL_CREATED.equals(event.eventType())) {
            return "NONE".equals(event.fromStatus()) && "CREATED".equals(event.toStatus());
        }
        return STATUS_CHANGED.equals(event.eventType())
                && event.fromStatus() != null
                && STATUS_VALUES.contains(event.fromStatus())
                && event.toStatus() != null
                && STATUS_VALUES.contains(event.toStatus());
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
    }
}
