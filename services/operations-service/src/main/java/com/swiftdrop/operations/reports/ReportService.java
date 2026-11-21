package com.swiftdrop.operations.reports;

import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.operations.courier.execution.client.ParcelResponse;
import com.swiftdrop.operations.courier.execution.client.ParcelServiceClient;
import com.swiftdrop.operations.zones.Zone;
import com.swiftdrop.operations.zones.ZoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
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
    private final DailyZoneReportRepository dailyZoneReportRepository;
    private final ZoneRepository zoneRepository;
    private final ParcelServiceClient parcelServiceClient;

    public ReportService(
            DailyReportRepository dailyReportRepository,
            ReportProcessedEventRepository processedEventRepository,
            ReportParcelSequenceRepository sequenceRepository
    ) {
        this(dailyReportRepository, processedEventRepository, sequenceRepository, null, null, null);
    }

    @Autowired
    public ReportService(
            DailyReportRepository dailyReportRepository,
            ReportProcessedEventRepository processedEventRepository,
            ReportParcelSequenceRepository sequenceRepository,
            @Autowired(required = false) DailyZoneReportRepository dailyZoneReportRepository,
            @Autowired(required = false) ZoneRepository zoneRepository,
            @Autowired(required = false) ParcelServiceClient parcelServiceClient
    ) {
        this.dailyReportRepository = dailyReportRepository;
        this.processedEventRepository = processedEventRepository;
        this.sequenceRepository = sequenceRepository;
        this.dailyZoneReportRepository = dailyZoneReportRepository;
        this.zoneRepository = zoneRepository;
        this.parcelServiceClient = parcelServiceClient;
    }

    @Transactional(readOnly = true)
    public List<DailyReportRow> getDailyReport(LocalDate from, LocalDate to) {
        return getDailyReport(from, to, null);
    }

    @Transactional(readOnly = true)
    public List<DailyReportRow> getDailyReport(LocalDate from, LocalDate to, String zone) {
        validateRange(from, to);

        if (zone != null && !zone.isBlank() && !"ALL".equalsIgnoreCase(zone.trim()) && dailyZoneReportRepository != null) {
            String targetZone = zone.trim();
            Map<LocalDate, DailyZoneReportAggregate> zoneAggregates = dailyZoneReportRepository
                    .findByZoneNameAndReportDateBetweenOrderByReportDate(targetZone, from, to)
                    .stream()
                    .collect(Collectors.toMap(DailyZoneReportAggregate::getReportDate, Function.identity(), (a, b) -> a));

            return from.datesUntil(to.plusDays(1))
                    .map(reportDate -> toZoneRow(reportDate, zoneAggregates.get(reportDate)))
                    .toList();
        }

        Map<LocalDate, DailyReportAggregate> aggregates = dailyReportRepository
                .findByReportDateBetweenOrderByReportDate(from, to)
                .stream()
                .collect(Collectors.toMap(DailyReportAggregate::getReportDate, Function.identity(), (a, b) -> a));

        return from.datesUntil(to.plusDays(1))
                .map(reportDate -> toRow(reportDate, aggregates.get(reportDate)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ZoneReportSummary> getZoneReport(LocalDate from, LocalDate to) {
        validateRange(from, to);

        List<Zone> configuredZones = zoneRepository != null ? zoneRepository.findAll() : List.of();
        List<DailyZoneReportAggregate> aggregates = dailyZoneReportRepository != null
                ? dailyZoneReportRepository.findByReportDateBetweenOrderByReportDate(from, to)
                : List.of();

        Map<String, List<DailyZoneReportAggregate>> groupedByZone = aggregates.stream()
                .collect(Collectors.groupingBy(DailyZoneReportAggregate::getZoneName));

        List<ZoneReportSummary> summaries = new ArrayList<>();
        Set<String> processedZoneNames = new HashSet<>();

        for (Zone zone : configuredZones) {
            String name = zone.getName();
            processedZoneNames.add(name.toUpperCase(Locale.ROOT));
            List<DailyZoneReportAggregate> records = groupedByZone.getOrDefault(name, List.of());
            int total = records.stream().mapToInt(DailyZoneReportAggregate::getTotalDeliveries).sum();
            int successful = records.stream().mapToInt(DailyZoneReportAggregate::getSuccessfulDeliveries).sum();
            int failed = records.stream().mapToInt(DailyZoneReportAggregate::getFailedDeliveries).sum();
            double successRate = total == 0 ? 0.0 : (double) successful / total;

            summaries.add(new ZoneReportSummary(
                    zone.getName(),
                    zone.getDistricts() != null ? zone.getDistricts() : Set.of(),
                    zone.isServed(),
                    total,
                    successful,
                    failed,
                    successRate
            ));
        }

        // Include any zones in aggregates that were not in configured zones
        for (Map.Entry<String, List<DailyZoneReportAggregate>> entry : groupedByZone.entrySet()) {
            if (!processedZoneNames.contains(entry.getKey().toUpperCase(Locale.ROOT))) {
                int total = entry.getValue().stream().mapToInt(DailyZoneReportAggregate::getTotalDeliveries).sum();
                int successful = entry.getValue().stream().mapToInt(DailyZoneReportAggregate::getSuccessfulDeliveries).sum();
                int failed = entry.getValue().stream().mapToInt(DailyZoneReportAggregate::getFailedDeliveries).sum();
                double successRate = total == 0 ? 0.0 : (double) successful / total;
                summaries.add(new ZoneReportSummary(
                        entry.getKey(),
                        Set.of(),
                        true,
                        total,
                        successful,
                        failed,
                        successRate
                ));
            }
        }

        summaries.sort(Comparator.comparingInt(ZoneReportSummary::totalDeliveries).reversed()
                .thenComparing(ZoneReportSummary::zoneName));
        return summaries;
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

    private DailyReportRow toZoneRow(LocalDate reportDate, DailyZoneReportAggregate aggregate) {
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

                recordZoneAggregate(event, reportDate, DELIVERED.equals(event.toStatus()));
            }
        }

        processedEventRepository.save(new ReportProcessedEvent(
                event.eventId(), event.eventType(), event.parcelId()));
    }

    private void recordZoneAggregate(EventEnvelope event, LocalDate reportDate, boolean isSuccess) {
        if (dailyZoneReportRepository == null) {
            return;
        }
        String zoneName = resolveZoneName(event);
        DailyZoneReportAggregate zoneAggregate = dailyZoneReportRepository
                .findByReportDateAndZoneName(reportDate, zoneName)
                .orElseGet(() -> new DailyZoneReportAggregate(reportDate, zoneName));
        if (isSuccess) {
            zoneAggregate.recordSuccess();
        } else {
            zoneAggregate.recordFailure();
        }
        dailyZoneReportRepository.save(zoneAggregate);
    }

    private String resolveZoneName(EventEnvelope event) {
        if (event.parcelId() != null && parcelServiceClient != null && zoneRepository != null) {
            try {
                ParcelResponse parcel = parcelServiceClient.getParcel(UUID.fromString(event.parcelId()));
                if (parcel != null && parcel.dropoffAddress() != null && parcel.dropoffAddress().district() != null) {
                    String district = parcel.dropoffAddress().district().trim();
                    return zoneRepository.findByDistrictsContaining(district)
                            .map(Zone::getName)
                            .orElse(district);
                }
            } catch (Exception ex) {
                // Fallback to default
            }
        }
        return "COLOMBO_ZONE";
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

