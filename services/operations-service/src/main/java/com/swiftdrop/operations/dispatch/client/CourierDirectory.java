package com.swiftdrop.operations.dispatch.client;

import java.util.List;
import java.util.Optional;

/**
 * Agreed interface contract for retrieving courier profiles and availability.
 * When M9's implementation in com.swiftdrop.operations.courier.profile merges,
 * this can be replaced by / adapted to M9's bean.
 */
public interface CourierDirectory {

    Optional<CourierSummary> findCourierById(String courierId);

    List<CourierSummary> listCouriersByZone(String zone, boolean availableOnly);

    List<CourierSummary> listAllCouriers();

    boolean isCourierAvailable(String courierId);

    void updateAvailability(String courierId, boolean available);
}
