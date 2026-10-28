package com.swiftdrop.operations.dispatch.client;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fake implementation of {@link CourierDirectory} used until M9 merges
 * the courier profile module.
 */
@Component
public class FakeCourierDirectory implements CourierDirectory {

    private final Map<String, CourierSummary> couriers = new ConcurrentHashMap<>();

    public FakeCourierDirectory() {
        seedDefaultCouriers();
    }

    private void seedDefaultCouriers() {
        registerCourier(new CourierSummary(
            "f8e7d6c5-b4a3-2109-8765-43210fedcba9",
            "Saman Perera",
            "+94771234567",
            "ZONE-COLOMBO-NORTH",
            true,
            "MOTORCYCLE"
        ));
        registerCourier(new CourierSummary(
            "a2b3c4d5-e6f7-4819-9012-34567890abcd",
            "Kamal Silva",
            "+94772345678",
            "ZONE-COLOMBO-SOUTH",
            true,
            "VAN"
        ));
        registerCourier(new CourierSummary(
            "b3c4d5e6-f7a8-4920-0123-45678901bcde",
            "Nimal Fernando",
            "+94773456789",
            "ZONE-COLOMBO-NORTH",
            false,
            "MOTORCYCLE"
        ));
    }

    public void registerCourier(CourierSummary courier) {
        couriers.put(courier.courierId(), courier);
    }

    @Override
    public Optional<CourierSummary> findCourierById(String courierId) {
        if (courierId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(couriers.get(courierId));
    }

    @Override
    public List<CourierSummary> listCouriersByZone(String zone, boolean availableOnly) {
        return couriers.values().stream()
            .filter(c -> zone == null || zone.isBlank() || zone.equalsIgnoreCase(c.zone()))
            .filter(c -> !availableOnly || c.available())
            .toList();
    }

    @Override
    public List<CourierSummary> listAllCouriers() {
        return new ArrayList<>(couriers.values());
    }

    @Override
    public boolean isCourierAvailable(String courierId) {
        if (courierId == null) {
            return false;
        }
        CourierSummary courier = couriers.get(courierId);
        return courier != null && courier.available();
    }

    @Override
    public void updateAvailability(String courierId, boolean available) {
        if (courierId == null) {
            return;
        }
        couriers.computeIfPresent(courierId, (id, current) ->
            new CourierSummary(
                current.courierId(),
                current.name(),
                current.phoneNumber(),
                current.zone(),
                available,
                current.vehicleType()
            )
        );
    }
}
