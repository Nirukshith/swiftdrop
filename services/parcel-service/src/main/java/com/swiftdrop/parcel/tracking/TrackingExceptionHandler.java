package com.swiftdrop.parcel.tracking;

import com.swiftdrop.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Error bodies for the tracking controllers. AccessDeniedException is left to the parcel
 * package's ParcelExceptionHandler so a 403 looks the same across parcel-service.
 * The content type is set explicitly so the JSON body is also sent to an EventSource
 * request (Accept: text/event-stream) instead of failing with 406.
 */
@RestControllerAdvice(assignableTypes = {
        TrackingController.class, TrackingStreamController.class, ParcelHistoryController.class})
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class TrackingExceptionHandler {

    static final String NOT_FOUND = "RESOURCE_NOT_FOUND";
    static final String UNAVAILABLE = "SERVICE_UNAVAILABLE";

    @ExceptionHandler(TrackingNotFoundException.class)
    ResponseEntity<ErrorResponse> trackingNotFound(TrackingNotFoundException ex, HttpServletRequest request) {
        return json(HttpStatus.NOT_FOUND, TrackingErrors.body(NOT_FOUND, TrackingNotFoundException.MESSAGE, request));
    }

    @ExceptionHandler(TrackingStreamsExhaustedException.class)
    ResponseEntity<ErrorResponse> streamsExhausted(TrackingStreamsExhaustedException ex, HttpServletRequest request) {
        return json(HttpStatus.SERVICE_UNAVAILABLE,
                TrackingErrors.body(UNAVAILABLE, TrackingStreamsExhaustedException.MESSAGE, request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String code = status == HttpStatus.NOT_FOUND ? NOT_FOUND
                : status != null ? status.name() : String.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : "Request failed";
        return json(ex.getStatusCode(), TrackingErrors.body(code, message, request));
    }

    private static ResponseEntity<ErrorResponse> json(HttpStatusCode status, ErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
