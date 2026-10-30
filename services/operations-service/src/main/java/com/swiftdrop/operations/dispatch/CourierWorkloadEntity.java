package com.swiftdrop.operations.dispatch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "courier_workload")
public class CourierWorkloadEntity {

    @Id
    @Column(name = "courier_id", length = 36, nullable = false)
    private String courierId;

    @Column(name = "active_count", nullable = false)
    private int activeCount;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "last_event_id", length = 36)
    private String lastEventId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CourierWorkloadEntity() {
    }

    public CourierWorkloadEntity(String courierId, int activeCount, long lastSequence, String lastEventId) {
        this.courierId = courierId;
        this.activeCount = Math.max(0, activeCount);
        this.lastSequence = lastSequence;
        this.lastEventId = lastEventId;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    public String getCourierId() {
        return courierId;
    }

    public void setCourierId(String courierId) {
        this.courierId = courierId;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(int activeCount) {
        this.activeCount = Math.max(0, activeCount);
        this.updatedAt = Instant.now();
    }

    public long getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(long lastSequence) {
        this.lastSequence = lastSequence;
        this.updatedAt = Instant.now();
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
