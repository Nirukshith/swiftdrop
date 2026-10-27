package com.swiftdrop.parcel.address;

import jakarta.validation.constraints.NotBlank;

public record AddressDto(
    @NotBlank(message = "Address line 1 is required")
    String line1,

    String line2,

    @NotBlank(message = "City is required")
    String city,

    @NotBlank(message = "District is required")
    String district,

    @NotBlank(message = "Postal code is required")
    String postalCode
) {}
