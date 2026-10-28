package com.swiftdrop.operations.dispatch;

import com.swiftdrop.common.CorrelationIdFilter;
import com.swiftdrop.common.ErrorResponse;
import com.swiftdrop.operations.dispatch.client.*;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice(basePackages = "com.swiftdrop.operations.dispatch")
public class DispatchExceptionHandler {

    @ExceptionHandler(ParcelAlreadyAssignedException.class)
    public ResponseEntity<ErrorResponse> handleParcelAlreadyAssigned(ParcelAlreadyAssignedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            new ErrorResponse(
                "PARCEL_ALREADY_ASSIGNED",
                ex.getMessage(),
                getCorrelationId(),
                Instant.now()
            )
        );
    }

    @ExceptionHandler(CourierUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCourierUnavailable(CourierUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new ErrorResponse(
                "COURIER_UNAVAILABLE",
                ex.getMessage(),
                getCorrelationId(),
                Instant.now()
            )
        );
    }

    @ExceptionHandler(ParcelNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleParcelNotFound(ParcelNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            new ErrorResponse(
                "PARCEL_NOT_FOUND",
                ex.getMessage(),
                getCorrelationId(),
                Instant.now()
            )
        );
    }

    @ExceptionHandler(CourierNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCourierNotFound(CourierNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            new ErrorResponse(
                "COURIER_NOT_FOUND",
                ex.getMessage(),
                getCorrelationId(),
                Instant.now()
            )
        );
    }

    private String getCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return (correlationId != null && !correlationId.isBlank()) ? correlationId : UUID.randomUUID().toString();
    }
}
