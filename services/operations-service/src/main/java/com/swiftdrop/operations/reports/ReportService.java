package com.swiftdrop.operations.reports;

import com.swiftdrop.common.EventEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReportService {

    static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Colombo");
    private static final String STATUS_CHANGED = "PARCEL_STATUS_CHANGED";
    private static final String DELIVERED = "DELIVERED";
    private static final String FAILED = "FAILED";

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
        return dailyReportRepository.findByReportDateBetweenOrderByReportDate(from, to)
                .stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public void processEvent(EventEnvelope event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()
                || event.parcelId() == null || event.parcelId().isBlank()
                || event.sequence() == null || event.occurredAt() == null) {
            throw new IllegalArgumentException("Reporting event is missing required envelope fields");
        }

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

    private DailyReportRow toRow(DailyReportAggregate aggregate) {
        int total = aggregate.getTotalDeliveries();
        double successRate = total == 0 ? 0.0 : (double) aggregate.getSuccessfulDeliveries() / total;
        return new DailyReportRow(
                aggregate.getReportDate(),
                total,
                aggregate.getSuccessfulDeliveries(),
                aggregate.getFailedDeliveries(),
                successRate
        );
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
    }
}
