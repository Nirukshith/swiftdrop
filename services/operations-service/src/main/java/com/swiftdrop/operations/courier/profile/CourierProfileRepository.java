package com.swiftdrop.operations.courier.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourierProfileRepository extends JpaRepository<CourierProfileEntity, String> {
    List<CourierProfileEntity> findByZoneIgnoreCase(String zone);
    List<CourierProfileEntity> findByAvailabilityTrue();
    List<CourierProfileEntity> findByZoneIgnoreCaseAndAvailabilityTrue(String zone);
}
