package com.swiftdrop.operations.dispatch.client;

public record CourierSummary(
    String courierId,
    String name,
    String phoneNumber,
    String zone,
    boolean available,
    String vehicleType
) {}
