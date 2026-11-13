package com.swiftdrop.operations.pricing;

import com.swiftdrop.common.ErrorResponse;
import com.swiftdrop.common.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.swiftdrop.operations.pricing")
public class PricingExceptionHandler {

    @ExceptionHandler(PricingException.class)
    public ResponseEntity<ErrorResponse> handlePricingException(
            PricingException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.code()) {
            case "UNKNOWN_DISTRICT", "PRICING_RULE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "PICKUP_DISTRICT_UNSERVED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        }
        return ResponseEntity.status(status).body(new ErrorResponse(
                exception.code(),
                exception.getMessage(),
                correlationId == null || correlationId.isBlank() ? "N/A" : correlationId,
                Instant.now()));
    }
}
