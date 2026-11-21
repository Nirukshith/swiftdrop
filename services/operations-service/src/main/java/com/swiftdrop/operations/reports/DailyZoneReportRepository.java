package com.swiftdrop.operations.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyZoneReportRepository extends JpaRepository<DailyZoneReportAggregate, UUID> {

    List<DailyZoneReportAggregate> findByReportDateBetweenOrderByReportDate(LocalDate from, LocalDate to);

    List<DailyZoneReportAggregate> findByZoneNameAndReportDateBetweenOrderByReportDate(String zoneName, LocalDate from, LocalDate to);

    Optional<DailyZoneReportAggregate> findByReportDateAndZoneName(LocalDate reportDate, String zoneName);
}

