package com.swiftdrop.operations.dispatch.client;

public record AddressClientDto(
    String line1,
    String line2,
    String city,
    String district,
    String postalCode
) {}
