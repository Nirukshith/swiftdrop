package com.swiftdrop.parcel.address;

import java.time.Instant;

public record AddressResponse(
    String id,
    String customerId,
    String label,
    String line1,
    String line2,
    String city,
    String district,
    String postalCode,
    boolean isDefault,
    Instant createdAt,
    Instant updatedAt
) {
    public static AddressResponse from(Address entity) {
        return new AddressResponse(
            entity.getId(),
            entity.getCustomerId(),
            entity.getLabel(),
            entity.getLine1(),
            entity.getLine2(),
            entity.getCity(),
            entity.getDistrict(),
            entity.getPostalCode(),
            entity.isDefault(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
