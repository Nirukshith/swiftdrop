package com.swiftdrop.operations.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "pricing_rules")
public class PricingRule {

    public enum Kind { WEIGHT_BAND, SIZE }

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;
    @Column(name = "rule_key", nullable = false)
    private String key;
    @Column(name = "minimum_weight_kg", precision = 10, scale = 3)
    private BigDecimal minimumWeightKg;
    @Column(name = "maximum_weight_kg", precision = 10, scale = 3)
    private BigDecimal maximumWeightKg;
    @Column(name = "charge_lkr", nullable = false, precision = 12, scale = 2)
    private BigDecimal chargeLkr;
    @Column(nullable = false)
    private boolean active;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PricingRule() {}

    public PricingRule(Kind kind, String key, BigDecimal minimumWeightKg, BigDecimal maximumWeightKg,
                       BigDecimal chargeLkr, boolean active) {
        this.id = UUID.randomUUID();
        replace(kind, key, minimumWeightKg, maximumWeightKg, chargeLkr, active);
    }

    public void replace(Kind kind, String key, BigDecimal minimumWeightKg, BigDecimal maximumWeightKg,
                        BigDecimal chargeLkr, boolean active) {
        this.kind = kind;
        this.key = key;
        this.minimumWeightKg = minimumWeightKg;
        this.maximumWeightKg = maximumWeightKg;
        this.chargeLkr = chargeLkr;
        this.active = active;
    }

    public UUID getId() { return id; }
    public Kind getKind() { return kind; }
    public String getKey() { return key; }
    public BigDecimal getMinimumWeightKg() { return minimumWeightKg; }
    public BigDecimal getMaximumWeightKg() { return maximumWeightKg; }
    public BigDecimal getChargeLkr() { return chargeLkr; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
