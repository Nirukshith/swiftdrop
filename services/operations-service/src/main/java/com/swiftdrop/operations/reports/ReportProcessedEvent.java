package com.swiftdrop.operations.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "report_processed_event")
public class ReportProcessedEvent {

    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "parcel_id", length = 36)
    private String parcelId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ReportProcessedEvent() {
    }

    public ReportProcessedEvent(String eventId, String eventType, String parcelId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.parcelId = parcelId;
    }

    @PrePersist
    void setProcessedAt() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }
}
