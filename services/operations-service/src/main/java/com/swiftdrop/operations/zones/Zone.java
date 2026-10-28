package com.swiftdrop.operations.zones;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "zones")
public class Zone {

    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String name;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "zone_districts", joinColumns = @JoinColumn(name = "zone_id"))
    @Column(name = "district", nullable = false)
    private Set<String> districts = new HashSet<>();
    @Column(nullable = false)
    private boolean served;
    @Column(name = "base_fee_lkr", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFeeLkr;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Zone() {
    }

    public Zone(String name, Set<String> districts, boolean served, BigDecimal baseFeeLkr) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.districts = new HashSet<>(districts);
        this.served = served;
        this.baseFeeLkr = baseFeeLkr;
    }

    public void replace(String name, Set<String> districts, boolean served, BigDecimal baseFeeLkr) {
        this.name = name;
        this.districts = new HashSet<>(districts);
        this.served = served;
        this.baseFeeLkr = baseFeeLkr;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Set<String> getDistricts() { return Set.copyOf(districts); }
    public boolean isServed() { return served; }
    public BigDecimal getBaseFeeLkr() { return baseFeeLkr; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
