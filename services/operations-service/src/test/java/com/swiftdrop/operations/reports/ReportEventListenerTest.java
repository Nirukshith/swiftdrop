package com.swiftdrop.operations.reports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftdrop.common.EventEnvelope;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ReportEventListenerTest {

    private final ReportService reportService = Mockito.mock(ReportService.class);
    private final ReportEventListener listener = new ReportEventListener(
            reportService, new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void deserializesValidEventsForTheReportService() throws Exception {
        String payload = """
                {
                  "eventId": "event-1",
                  "eventType": "PARCEL_STATUS_CHANGED",
                  "occurredAt": "2026-11-01T10:00:00Z",
                  "sequence": 2,
                  "parcelId": "parcel-1",
                  "trackingNumber": "SD-20261101-1",
                  "fromStatus": "OUT_FOR_DELIVERY",
                  "toStatus": "DELIVERED"
                }
                """;

        listener.onEvent(payload);

        verify(reportService).processEvent(Mockito.any(EventEnvelope.class));
    }

    @Test
    void rejectsMalformedPayloadForRetryAndDltHandling() {
        assertThatThrownBy(() -> listener.onEvent("{not-json"))
                .isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(reportService);
    }

    @Test
    void dltHandlerDoesNotApplyExhaustedPayload() {
        listener.handleDlt("{\"eventId\":\"event-1\"}", "parcel.status-changed.v1.dlt");

        verifyNoInteractions(reportService);
    }
}
