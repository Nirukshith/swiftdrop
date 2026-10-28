package com.swiftdrop.operations.courier.profile;

public record CourierSummary(
    String courierId,
    String name,
    String phoneNumber,
    String zone,
    boolean available,
    String vehicleType
) {}
