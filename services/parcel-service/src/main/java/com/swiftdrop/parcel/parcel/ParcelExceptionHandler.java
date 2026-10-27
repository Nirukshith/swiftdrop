package com.swiftdrop.parcel.parcel;

import com.swiftdrop.common.CorrelationIdFilter;
import com.swiftdrop.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice(basePackages = "com.swiftdrop.parcel")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ParcelExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ErrorResponse error = new ErrorResponse(
                "OPTIMISTIC_LOCK_CONFLICT",
                "Parcel was updated concurrently by another request. Please reload and retry.",
                correlationId,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateTransition(
            IllegalStateTransitionException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ErrorResponse error = new ErrorResponse(
                "ILLEGAL_STATUS_TRANSITION",
                ex.getMessage(),
                correlationId,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        String correlationId = resolveCorrelationId(request);
        ErrorResponse error = new ErrorResponse(
                "FORBIDDEN",
                ex.getMessage() != null ? ex.getMessage() : "Access denied",
                correlationId,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        }
        return correlationId != null ? correlationId : "N/A";
    }
}
