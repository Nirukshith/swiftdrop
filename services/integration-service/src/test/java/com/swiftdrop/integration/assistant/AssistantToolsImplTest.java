package com.swiftdrop.integration.assistant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.swiftdrop.integration.assistant.tools.AssistantToolsImpl;
import com.swiftdrop.integration.assistant.tools.ParcelServiceClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantToolsImplTest {

    @Test
    void rejectsMalformedTrackingBeforeCallingParcelService() {
        ParcelServiceClient client = mock(ParcelServiceClient.class);
        AssistantToolsImpl tools = new AssistantToolsImpl(client);

        assertThrows(IllegalArgumentException.class,
                () -> tools.getParcelByTrackingNumber("not-a-tracking-number", "token"));
        verifyNoInteractions(client);
    }

    @Test
    void refusesParcelThatIsNotInAuthenticatedCustomerList() {
        ParcelServiceClient client = mock(ParcelServiceClient.class);
        when(client.list(null, 0, 50, "token"))
                .thenReturn(new ParcelServiceClient.ParcelPage(List.of(), 0, 0, 0, 50));
        AssistantToolsImpl tools = new AssistantToolsImpl(client);

        assertThrows(AssistantToolsImpl.ParcelNotFoundException.class,
                () -> tools.getParcelByTrackingNumber("SD-20261024-ABC123", "token"));
        verify(client).list(null, 0, 50, "token");
        verifyNoMoreInteractions(client);
    }

    @Test
    void returnsOnlyOwnedParcelSummaryCounts() {
        ParcelServiceClient client = mock(ParcelServiceClient.class);
        when(client.list(null, 0, 50, "token")).thenReturn(new ParcelServiceClient.ParcelPage(List.of(
                new ParcelServiceClient.ParcelListItem("id-1", "SD-20261024-ABC123", "DELIVERED", null,
                        Instant.EPOCH, Instant.EPOCH, null),
                new ParcelServiceClient.ParcelListItem("id-2", "SD-20261024-DEF456", "IN_TRANSIT", "courier",
                        Instant.EPOCH, Instant.EPOCH, null)), 2, 1, 0, 50));
        AssistantToolsImpl tools = new AssistantToolsImpl(client);

        var summary = tools.getParcelSummary("token");

        assertEquals(2, summary.total());
        assertEquals(1, summary.statusCounts().get("DELIVERED"));
        assertEquals(1, summary.statusCounts().get("IN_TRANSIT"));
    }
}
