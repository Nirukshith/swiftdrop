package com.swiftdrop.operations.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "report_parcel_sequence")
public class ReportParcelSequence {

    @Id
    @Column(name = "parcel_id", length = 36, nullable = false)
    private String parcelId;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReportParcelSequence() {
    }

    public ReportParcelSequence(String parcelId, long lastSequence) {
        this.parcelId = parcelId;
        this.lastSequence = lastSequence;
    }

    public long getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(long lastSequence) {
        this.lastSequence = lastSequence;
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
