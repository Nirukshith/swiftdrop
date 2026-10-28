package com.swiftdrop.operations.courier.profile;

public record CourierProfileResponse(
    String userId,
    String name,
    String phone,
    String zone,
    boolean availability,
    String vehicle
) {}
