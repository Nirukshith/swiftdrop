package com.swiftdrop.parcel.parcel;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parcels")
@Getter
@Setter
@NoArgsConstructor
public class Parcel {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "tracking_number", length = 32, nullable = false, unique = true)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParcelStatus status;

    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Column(name = "merchant_id", length = 36)
    private String merchantId;

    @Column(name = "courier_id", length = 36)
    private String courierId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "line1", column = @Column(name = "pickup_line1", nullable = false)),
            @AttributeOverride(name = "line2", column = @Column(name = "pickup_line2")),
            @AttributeOverride(name = "city", column = @Column(name = "pickup_city", nullable = false)),
            @AttributeOverride(name = "district", column = @Column(name = "pickup_district", nullable = false)),
            @AttributeOverride(name = "postalCode", column = @Column(name = "pickup_postal_code", nullable = false))
    })
    private AddressSnapshot pickupAddress;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "line1", column = @Column(name = "dropoff_line1", nullable = false)),
            @AttributeOverride(name = "line2", column = @Column(name = "dropoff_line2")),
            @AttributeOverride(name = "city", column = @Column(name = "dropoff_city", nullable = false)),
            @AttributeOverride(name = "district", column = @Column(name = "dropoff_district", nullable = false)),
            @AttributeOverride(name = "postalCode", column = @Column(name = "dropoff_postal_code", nullable = false))
    })
    private AddressSnapshot dropoffAddress;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParcelSize size;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fee;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 50)
    private String recipientPhone;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Embedded
    private DeliveryProof proof;

    @Column(nullable = false)
    private Long sequence = 1L;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ParcelStatus.CREATED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
