package com.swiftdrop.operations.courier.execution.dto;

import java.util.List;

public record CourierDeliveriesResponse(
    String date,
    Integer totalDeliveries,
    Integer pendingCount,
    Integer completedCount,
    List<CourierDeliveryCard> deliveries
) {}
