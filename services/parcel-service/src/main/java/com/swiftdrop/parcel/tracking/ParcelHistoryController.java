package com.swiftdrop.parcel.tracking;

import com.swiftdrop.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated status timeline of one parcel (CU-05). */
@RestController
class ParcelHistoryController {

    private final TrackingService trackingService;

    ParcelHistoryController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/api/v1/parcels/{id}/history")
    ParcelHistoryResponse getParcelHistory(@PathVariable String id) {
        return trackingService.history(id, CurrentUser.require());
    }
}
