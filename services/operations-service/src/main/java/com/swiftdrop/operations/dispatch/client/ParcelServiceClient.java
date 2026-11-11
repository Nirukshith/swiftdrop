package com.swiftdrop.operations.dispatch.client;

import java.time.LocalDate;

public interface ParcelServiceClient {

    ParcelPageClientResponse queryUnassignedParcels(String zone, LocalDate date, int page, int size);

    ParcelPageClientResponse queryFailedParcels(String zone, LocalDate date, int page, int size);

    default ParcelClientResponse assignCourier(String parcelId, String courierId, String assignedBy) {
        return assignCourier(parcelId, courierId, assignedBy, false);
    }

    ParcelClientResponse assignCourier(String parcelId, String courierId, String assignedBy, boolean reassign);

    ParcelClientResponse requeueParcel(String parcelId, String reason, String requeuedBy);
}

