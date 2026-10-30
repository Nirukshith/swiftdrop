package com.swiftdrop.operations.courier.profile;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Primary
@Component
public class M4CourierDirectoryAdapter implements com.swiftdrop.operations.dispatch.client.CourierDirectory {
    private final CourierDirectory directory;

    public M4CourierDirectoryAdapter(CourierDirectory directory) {
        this.directory = directory;
    }

    @Override
    public Optional<com.swiftdrop.operations.dispatch.client.CourierSummary> findCourierById(String courierId) {
        return directory.findCourierById(courierId).map(this::toM4Summary);
    }

    @Override
    public List<com.swiftdrop.operations.dispatch.client.CourierSummary> listCouriersByZone(String zone, boolean availableOnly) {
        return directory.listCouriersByZone(zone, availableOnly).stream().map(this::toM4Summary).toList();
    }

    @Override
    public List<com.swiftdrop.operations.dispatch.client.CourierSummary> listAllCouriers() {
        return directory.listAllCouriers().stream().map(this::toM4Summary).toList();
    }

    @Override
    public boolean isCourierAvailable(String courierId) {
        return directory.isCourierAvailable(courierId);
    }

    @Override
    public void updateAvailability(String courierId, boolean available) {
        directory.updateAvailability(courierId, available);
    }

    private com.swiftdrop.operations.dispatch.client.CourierSummary toM4Summary(CourierSummary summary) {
        return new com.swiftdrop.operations.dispatch.client.CourierSummary(summary.courierId(), summary.name(), summary.phoneNumber(), summary.zone(), summary.available(), summary.vehicleType());
    }
}
