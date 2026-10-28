package com.swiftdrop.integration.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_orders")
public class MerchantOrderEntity {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "parcel_id", nullable = false, unique = true)
    private UUID parcelId;

    @Column(name = "tracking_number", nullable = false, length = 64)
    private String trackingNumber;

    @Column(name = "external_reference", nullable = false, length = 128)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MerchantOrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal fee;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MerchantOrderEntity() {}

    public MerchantOrderEntity(UUID orderId, UUID merchantId, UUID parcelId, String trackingNumber,
                               String externalReference, MerchantOrderStatus status, BigDecimal fee,
                               Instant createdAt) {
        this.orderId = orderId;
        this.merchantId = merchantId;
        this.parcelId = parcelId;
        this.trackingNumber = trackingNumber;
        this.externalReference = externalReference;
        this.status = status;
        this.fee = fee;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public MerchantOrder toReadModel() {
        return new MerchantOrder(orderId, merchantId, parcelId, trackingNumber,
                externalReference, status, createdAt);
    }

    public void updateStatus(MerchantOrderStatus status, Instant updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public UUID getOrderId() { return orderId; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getParcelId() { return parcelId; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getExternalReference() { return externalReference; }
    public MerchantOrderStatus getStatus() { return status; }
    public BigDecimal getFee() { return fee; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
