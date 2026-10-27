package com.swiftdrop.parcel.parcel;

import com.swiftdrop.common.EventEnvelope;

import java.time.Instant;
import java.util.UUID;

public record ParcelDomainEvent(
        String topic,
        String parcelId,
        EventEnvelope envelope
) {
    public String getTopic() {
        return topic;
    }

    public String getParcelId() {
        return parcelId;
    }

    public EventEnvelope getEnvelope() {
        return envelope;
    }

    public static ParcelDomainEvent created(Parcel parcel) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                "PARCEL_CREATED",
                Instant.now(),
                parcel.getSequence() != null ? parcel.getSequence() : 1L,
                parcel.getId(),
                parcel.getTrackingNumber(),
                "NONE",
                "CREATED",
                null,
                null,
                parcel.getMerchantId(),
                parcel.getCustomerId(),
                null
        );
        return new ParcelDomainEvent("parcel.created.v1", parcel.getId(), envelope);
    }

    public static ParcelDomainEvent assigned(Parcel parcel, String previousCourierId) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                "PARCEL_ASSIGNED",
                Instant.now(),
                parcel.getSequence(),
                parcel.getId(),
                parcel.getTrackingNumber(),
                parcel.getStatus().name(),
                parcel.getStatus().name(),
                parcel.getCourierId(),
                previousCourierId,
                parcel.getMerchantId(),
                parcel.getCustomerId(),
                null
        );
        return new ParcelDomainEvent("parcel.assigned.v1", parcel.getId(), envelope);
    }

    public static ParcelDomainEvent statusChanged(
            Parcel parcel,
            ParcelStatus fromStatus,
            ParcelStatus toStatus,
            String reason,
            String previousCourierId
    ) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                "PARCEL_STATUS_CHANGED",
                Instant.now(),
                parcel.getSequence(),
                parcel.getId(),
                parcel.getTrackingNumber(),
                fromStatus.name(),
                toStatus.name(),
                parcel.getCourierId(),
                previousCourierId,
                parcel.getMerchantId(),
                parcel.getCustomerId(),
                reason
        );
        return new ParcelDomainEvent("parcel.status-changed.v1", parcel.getId(), envelope);
    }
}
