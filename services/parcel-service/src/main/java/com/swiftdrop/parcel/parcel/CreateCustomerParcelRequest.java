package com.swiftdrop.parcel.parcel;

import com.swiftdrop.parcel.address.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateCustomerParcelRequest(
        @NotNull(message = "Pickup address is required")
        @Valid
        AddressDto pickupAddress,

        @NotNull(message = "Dropoff address is required")
        @Valid
        AddressDto dropoffAddress,

        @NotNull(message = "Weight is required")
        @DecimalMin(value = "0.10", message = "Weight must be at least 0.10 kg")
        @DecimalMax(value = "50.00", message = "Weight cannot exceed 50.00 kg")
        BigDecimal weightKg,

        @NotNull(message = "Parcel size is required")
        ParcelSize size,

        @NotBlank(message = "Recipient name is required")
        String recipientName,

        @NotBlank(message = "Recipient phone is required")
        String recipientPhone,

        String notes
) {}
