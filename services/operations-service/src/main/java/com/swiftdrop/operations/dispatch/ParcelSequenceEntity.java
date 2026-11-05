package com.swiftdrop.operations.dispatch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "parcel_sequence")
public class ParcelSequenceEntity {

    @Id
    @Column(name = "parcel_id", length = 36, nullable = false)
    private String parcelId;

    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ParcelSequenceEntity() {
    }

    public ParcelSequenceEntity(String parcelId, Long lastSequence) {
        this.parcelId = parcelId;
        this.lastSequence = lastSequence;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getParcelId() {
        return parcelId;
    }

    public void setParcelId(String parcelId) {
        this.parcelId = parcelId;
    }

    public Long getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(Long lastSequence) {
        this.lastSequence = lastSequence;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

