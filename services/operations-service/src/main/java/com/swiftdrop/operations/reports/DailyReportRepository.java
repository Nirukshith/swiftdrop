package com.swiftdrop.operations.reports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyReportRepository extends JpaRepository<DailyReportAggregate, LocalDate> {

    List<DailyReportAggregate> findByReportDateBetweenOrderByReportDate(LocalDate from, LocalDate to);
}
