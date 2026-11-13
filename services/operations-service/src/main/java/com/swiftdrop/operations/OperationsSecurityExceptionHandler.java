package com.swiftdrop.operations;

import com.swiftdrop.common.ErrorResponse;
import com.swiftdrop.common.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.swiftdrop.operations")
public class OperationsSecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            Exception exception, HttpServletRequest request) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
                "FORBIDDEN",
                "Access is denied",
                correlationId == null || correlationId.isBlank() ? "N/A" : correlationId,
                Instant.now()));
    }
}
