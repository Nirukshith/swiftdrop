package com.swiftdrop.operations.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_report_aggregate")
public class DailyReportAggregate {

    @Id
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "total_deliveries", nullable = false)
    private int totalDeliveries;

    @Column(name = "successful_deliveries", nullable = false)
    private int successfulDeliveries;

    @Column(name = "failed_deliveries", nullable = false)
    private int failedDeliveries;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DailyReportAggregate() {
    }

    public DailyReportAggregate(LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    public void recordSuccess() {
        totalDeliveries++;
        successfulDeliveries++;
    }

    public void recordFailure() {
        totalDeliveries++;
        failedDeliveries++;
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public int getTotalDeliveries() {
        return totalDeliveries;
    }

    public int getSuccessfulDeliveries() {
        return successfulDeliveries;
    }

    public int getFailedDeliveries() {
        return failedDeliveries;
    }
}
