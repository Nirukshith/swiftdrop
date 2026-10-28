package com.swiftdrop.parcel.tracking;

import com.swiftdrop.common.CorrelationIdFilter;
import com.swiftdrop.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

/** Builds the shared ErrorResponse body for the tracking package. */
final class TrackingErrors {

    private TrackingErrors() {
    }

    static ErrorResponse body(String code, String message, HttpServletRequest request) {
        return new ErrorResponse(code, message, correlationId(request), Instant.now());
    }

    private static String correlationId(HttpServletRequest request) {
        String id = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (id == null || id.isBlank()) {
            id = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        }
        return id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
    }
}
