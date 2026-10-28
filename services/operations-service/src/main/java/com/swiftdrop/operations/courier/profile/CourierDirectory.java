package com.swiftdrop.operations.courier.profile;

import java.util.List;
import java.util.Optional;

public interface CourierDirectory {
    Optional<CourierSummary> findCourierById(String courierId);
    List<CourierSummary> listCouriersByZone(String zone, boolean availableOnly);
    List<CourierSummary> listAllCouriers();
    boolean isCourierAvailable(String courierId);
    void updateAvailability(String courierId, boolean available);
}
