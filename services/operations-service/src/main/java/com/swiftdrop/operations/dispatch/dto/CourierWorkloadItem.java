package com.swiftdrop.operations.dispatch.dto;

public record CourierWorkloadItem(
    String courierId,
    String name,
    String phoneNumber,
    String zone,
    int activeCount,
    boolean available,
    String vehicleType
) {}
