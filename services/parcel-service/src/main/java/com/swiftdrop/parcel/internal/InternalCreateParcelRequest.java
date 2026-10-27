package com.swiftdrop.parcel.internal;

import com.swiftdrop.parcel.address.AddressDto;
import com.swiftdrop.parcel.parcel.ParcelSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InternalCreateParcelRequest(
        @NotBlank(message = "Merchant ID is required")
        String merchantId,

        String externalReference,

        @NotNull(message = "Pickup address is required")
        @Valid
        AddressDto pickupAddress,

        @NotNull(message = "Dropoff address is required")
        @Valid
        AddressDto dropoffAddress,

        @NotNull(message = "Weight is required")
        @DecimalMin(value = "0.10", message = "Weight must be at least 0.10 kg")
        BigDecimal weightKg,

        @NotNull(message = "Parcel size is required")
        ParcelSize size,

        @NotNull(message = "Fee is required")
        @DecimalMin(value = "0.00", message = "Fee cannot be negative")
        BigDecimal fee,

        @NotBlank(message = "Recipient name is required")
        String recipientName,

        @NotBlank(message = "Recipient phone is required")
        String recipientPhone
) {}
