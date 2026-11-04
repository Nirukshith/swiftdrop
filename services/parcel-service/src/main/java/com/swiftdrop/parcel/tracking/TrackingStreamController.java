package com.swiftdrop.parcel.tracking;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Public live tracking stream (CU-04). No login; masked like the lookup; rate limited per IP. */
@RestController
class TrackingStreamController {

    static final String LAST_EVENT_ID = "Last-Event-ID";

    private final TrackingStreamService streamService;

    TrackingStreamController(TrackingStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping("/api/v1/tracking/{trackingNo}/stream")
    ResponseEntity<SseEmitter> stream(@PathVariable String trackingNo,
                                      @RequestHeader(name = LAST_EVENT_ID, required = false) String lastEventId) {
        SseEmitter emitter = streamService.open(trackingNo, lastEventId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no") // tells Nginx not to buffer events
                .body(emitter);
    }
}
