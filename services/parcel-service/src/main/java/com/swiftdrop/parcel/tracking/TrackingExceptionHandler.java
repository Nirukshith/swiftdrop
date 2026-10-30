package com.swiftdrop.parcel.tracking;

import com.swiftdrop.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Error bodies for the tracking controllers. AccessDeniedException is left to the parcel
 * package's ParcelExceptionHandler so a 403 looks the same across parcel-service.
 */
@RestControllerAdvice(assignableTypes = {TrackingController.class, ParcelHistoryController.class})
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class TrackingExceptionHandler {

    static final String NOT_FOUND = "RESOURCE_NOT_FOUND";

    @ExceptionHandler(TrackingNotFoundException.class)
    ResponseEntity<ErrorResponse> trackingNotFound(TrackingNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(TrackingErrors.body(NOT_FOUND, TrackingNotFoundException.MESSAGE, request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String code = status == HttpStatus.NOT_FOUND ? NOT_FOUND
                : status != null ? status.name() : String.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : "Request failed";
        return ResponseEntity.status(ex.getStatusCode()).body(TrackingErrors.body(code, message, request));
    }
}
