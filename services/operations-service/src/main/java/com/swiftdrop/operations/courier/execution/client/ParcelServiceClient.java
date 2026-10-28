package com.swiftdrop.operations.courier.execution.client;

import java.util.UUID;

public interface ParcelServiceClient {

    ParcelPageResponse getParcelsByCourierAndDate(String courierId, String date);

    ParcelResponse getParcel(UUID parcelId);

    ParcelResponse updateStatus(UUID parcelId, ParcelStatusUpdateRequest request);
}
