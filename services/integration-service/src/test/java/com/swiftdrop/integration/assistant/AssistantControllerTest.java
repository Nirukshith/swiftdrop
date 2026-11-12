package com.swiftdrop.integration.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.swiftdrop.integration.assistant.client.FakeLlmClient;
import com.swiftdrop.integration.assistant.client.LlmClient;
import com.swiftdrop.integration.assistant.controller.AssistantController;
import com.swiftdrop.integration.assistant.dto.ChatMessageRequest;
import com.swiftdrop.integration.assistant.dto.ChatMessageResponse;
import com.swiftdrop.integration.assistant.service.AssistantService;
import com.swiftdrop.integration.assistant.tools.AssistantTools;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AssistantControllerTest {

    private AssistantTools tools;
    private AssistantController controller;
    private Jwt customerJwt;

    @BeforeEach
    void setUp() {
        tools = mock(AssistantTools.class);
        controller = new AssistantController(new AssistantService(new FakeLlmClient(), tools));
        customerJwt = Jwt.withTokenValue("customer-token").header("alg", "none")
                .subject("customer-1").claim("role", "CUSTOMER").build();
    }

    @Test
    void answersWithFactsReturnedForOwnedParcel() {
        when(tools.getParcelByTrackingNumber("SD-20261024-ABC123", "customer-token"))
                .thenReturn(new AssistantTools.ParcelLookupResult(
                        "SD-20261024-ABC123", "IN_TRANSIT", null, true, Instant.parse("2026-10-24T10:00:00Z"), null));

        var response = controller.chat(
                new ChatMessageRequest("Where is parcel SD-20261024-ABC123?", null), customerJwt, null);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().reply().contains("IN_TRANSIT"));
        verify(tools).getParcelByTrackingNumber("SD-20261024-ABC123", "customer-token");
    }

    @Test
    void asksForTrackingNumberWhenParcelQuestionIsAmbiguous() {
        ChatMessageResponse response = controller.chat(
                new ChatMessageRequest("Where is my parcel?", "conv-1"), customerJwt, null).getBody();

        assertEquals("Please provide the tracking number so I can check the correct parcel.", response.reply());
        verifyNoInteractions(tools);
    }

    @Test
    void rejectsUnsupportedQuestionWithoutCallingTools() {
        ChatMessageResponse response = controller.chat(
                new ChatMessageRequest("Can you tell me the weather?", null), customerJwt, null).getBody();

        assertTrue(response.reply().contains("SwiftDrop parcels"));
        verifyNoInteractions(tools);
    }

    @Test
    void reportsUnavailableParcelWithoutInventingDetails() {
        when(tools.getParcelByTrackingNumber(anyString(), anyString()))
                .thenThrow(new com.swiftdrop.integration.assistant.tools.AssistantToolsImpl.ParcelNotFoundException());

        ChatMessageResponse response = controller.chat(
                new ChatMessageRequest("What is the status of SD-20261024-ABC123?", null), customerJwt, null).getBody();

        assertTrue(response.reply().contains("could not find"));
    }

    @Test
    void groundsFeeAnswerInPricingToolResult() {
        when(tools.estimateFee("Colombo", "Kandy", 2.0, "MEDIUM", "customer-token"))
                .thenReturn(new AssistantTools.FeeEstimateResult(2.0, "MEDIUM", "Kandy",
                        java.math.BigDecimal.valueOf(650), "LKR"));

        ChatMessageResponse response = controller.chat(
                new ChatMessageRequest("What is the fee from Colombo to Kandy for 2 kg medium?", null),
                customerJwt, null).getBody();

        assertTrue(response.reply().contains("650"));
        verify(tools).estimateFee("Colombo", "Kandy", 2.0, "MEDIUM", "customer-token");
    }

    @Test
    void reportsProviderUnavailableWithoutExposingProviderDetails() {
        LlmClient unavailable = (prompt, conversationId, token) -> {
            throw new IllegalStateException("provider secret");
        };
        AssistantController unavailableController =
                new AssistantController(new AssistantService(unavailable, tools));
        when(tools.getCustomerParcels(null, 0, 20, "customer-token")).thenReturn(List.of());

        ChatMessageResponse response = unavailableController.chat(
                new ChatMessageRequest("Show my recent parcels", null), customerJwt, null).getBody();

        assertTrue(response.reply().contains("temporarily unavailable"));
        assertTrue(!response.reply().contains("provider secret"));
    }
}
