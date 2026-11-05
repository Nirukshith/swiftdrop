package com.swiftdrop.parcel.receipt;

import com.swiftdrop.common.CorrelationIdFilter;
import com.swiftdrop.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Error bodies for the receipt endpoint, using the codes in parcel-read.yaml. The JSON content
 * type is set explicitly so an error is still sent when the client asked for application/pdf
 * (otherwise content negotiation drops the body).
 */
@RestControllerAdvice(assignableTypes = ReceiptController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class ReceiptExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReceiptExceptionHandler.class);

    static final String ACCESS_DENIED = "ACCESS_DENIED";
    static final String NOT_FOUND = "RESOURCE_NOT_FOUND";
    static final String NOT_DELIVERED = "PARCEL_NOT_DELIVERED";
    static final String GENERATION_FAILED = "RECEIPT_GENERATION_FAILED";

    @ExceptionHandler(ReceiptAccessDeniedException.class)
    ResponseEntity<ErrorResponse> accessDenied(ReceiptAccessDeniedException ex, HttpServletRequest request) {
        return json(HttpStatus.FORBIDDEN, ACCESS_DENIED, ReceiptAccessDeniedException.MESSAGE, request);
    }

    @ExceptionHandler(ParcelNotDeliveredException.class)
    ResponseEntity<ErrorResponse> notDelivered(ParcelNotDeliveredException ex, HttpServletRequest request) {
        return json(HttpStatus.CONFLICT, NOT_DELIVERED, ex.getMessage(), request);
    }

    @ExceptionHandler(ReceiptGenerationException.class)
    ResponseEntity<ErrorResponse> generationFailed(ReceiptGenerationException ex, HttpServletRequest request) {
        // Only the exception type is logged: the cause message could contain receipt content.
        log.error("Receipt PDF generation failed: {}", ex.getCause() != null ? ex.getCause().getClass().getName() : "unknown");
        return json(HttpStatus.INTERNAL_SERVER_ERROR, GENERATION_FAILED,
                "The receipt could not be generated. Please try again later.", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND) {
            return json(ex.getStatusCode(), NOT_FOUND, "Parcel not found", request);
        }
        String code = status != null ? status.name() : String.valueOf(ex.getStatusCode().value());
        return json(ex.getStatusCode(), code, ex.getReason() != null ? ex.getReason() : "Request failed", request);
    }

    private static ResponseEntity<ErrorResponse> json(HttpStatusCode status, String code, String message,
                                                      HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(code, message, correlationId(request), Instant.now());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static String correlationId(HttpServletRequest request) {
        String id = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (id == null || id.isBlank()) {
            id = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        }
        return id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
    }
}
