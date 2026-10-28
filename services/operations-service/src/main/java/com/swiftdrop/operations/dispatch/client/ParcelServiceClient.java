package com.swiftdrop.operations.dispatch.client;

import java.time.LocalDate;

public interface ParcelServiceClient {

    ParcelPageClientResponse queryUnassignedParcels(String zone, LocalDate date, int page, int size);

    ParcelClientResponse assignCourier(String parcelId, String courierId, String assignedBy);
}
