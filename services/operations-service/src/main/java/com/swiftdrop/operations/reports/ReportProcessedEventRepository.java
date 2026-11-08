package com.swiftdrop.operations.reports;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportProcessedEventRepository extends JpaRepository<ReportProcessedEvent, String> {
}
