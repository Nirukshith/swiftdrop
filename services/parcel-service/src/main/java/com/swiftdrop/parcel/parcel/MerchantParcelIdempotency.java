package com.swiftdrop.parcel.parcel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "merchant_parcel_idempotency",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_merchant_parcel_idempotency_scope",
                columnNames = {"merchant_id", "operation", "idempotency_key"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class MerchantParcelIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false, length = 36)
    private String merchantId;

    @Column(nullable = false, length = 64)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "parcel_id", length = 36)
    private String parcelId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
