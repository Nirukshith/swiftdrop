package com.swiftdrop.operations;

import com.swiftdrop.common.CorrelationIdFilter;
import com.swiftdrop.operations.pricing.PricingException;
import com.swiftdrop.operations.pricing.PricingExceptionHandler;
import com.swiftdrop.operations.reports.ReportExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExceptionHandlerCorrelationIdTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @AfterEach
    void clearCorrelationId() {
        MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    }

    @Test
    void pricingHandlerPrefersMdcCorrelationId() {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("header-id");
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "mdc-id");

        ResponseEntity<?> response = new PricingExceptionHandler()
                .handlePricingException(new PricingException("UNKNOWN_DISTRICT", "unknown"), request);

        assertThat(response.getBody()).extracting("correlationId").isEqualTo("mdc-id");
    }

    @Test
    void pricingHandlerFallsBackToHeaderCorrelationId() {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("header-id");

        ResponseEntity<?> response = new PricingExceptionHandler()
                .handlePricingException(new PricingException("UNKNOWN_DISTRICT", "unknown"), request);

        assertThat(response.getBody()).extracting("correlationId").isEqualTo("header-id");
    }

    @Test
    void reportHandlerPrefersMdcCorrelationId() {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("header-id");
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "mdc-id");

        ResponseEntity<?> response = new ReportExceptionHandler().handleInvalidDateParameter(
                new MissingServletRequestParameterException("from", "LocalDate"), request);

        assertThat(response.getBody()).extracting("correlationId").isEqualTo("mdc-id");
    }

    @Test
    void reportHandlerFallsBackToHeaderCorrelationId() {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("header-id");

        ResponseEntity<?> response = new ReportExceptionHandler().handleInvalidDateParameter(
                new MissingServletRequestParameterException("from", "LocalDate"), request);

        assertThat(response.getBody()).extracting("correlationId").isEqualTo("header-id");
    }

    @Test
    void securityHandlerPrefersMdcCorrelationId() {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("header-id");
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "mdc-id");

        ResponseEntity<?> response = new OperationsSecurityExceptionHandler()
                .handleAccessDenied(new AccessDeniedException("denied"), request);

        assertThat(response.getBody()).extracting("correlationId").isEqualTo("mdc-id");
    }

    @Test
    void securityHandlerFallsBackToHeaderCorrelationId() {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("header-id");

        ResponseEntity<?> response = new OperationsSecurityExceptionHandler()
                .handleAccessDenied(new AccessDeniedException("denied"), request);

        assertThat(response.getBody()).extracting("correlationId").isEqualTo("header-id");
    }
}
