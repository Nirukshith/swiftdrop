package com.swiftdrop.parcel.tracking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public masked tracking lookup (CU-06). No login; rate limited per IP. */
@RestController
class TrackingController {

    private final TrackingService trackingService;

    TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/api/v1/tracking/{trackingNo}")
    PublicTrackingResponse getPublicTracking(@PathVariable String trackingNo) {
        return trackingService.publicTracking(trackingNo).orElseThrow(TrackingNotFoundException::new);
    }
}
