package com.swiftdrop.operations.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_zone_report_aggregate")
public class DailyZoneReportAggregate {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "zone_name", nullable = false, length = 128)
    private String zoneName;

    @Column(name = "total_deliveries", nullable = false)
    private int totalDeliveries;

    @Column(name = "successful_deliveries", nullable = false)
    private int successfulDeliveries;

    @Column(name = "failed_deliveries", nullable = false)
    private int failedDeliveries;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DailyZoneReportAggregate() {
    }

    public DailyZoneReportAggregate(LocalDate reportDate, String zoneName) {
        this.id = UUID.randomUUID();
        this.reportDate = reportDate;
        this.zoneName = zoneName;
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
        if (id == null) {
            id = UUID.randomUUID();
        }
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public String getZoneName() {
        return zoneName;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

