package com.swiftdrop.integration.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.swiftdrop.integration.assistant.client.FakeLlmClient;
import com.swiftdrop.integration.assistant.client.LlmClient;
import com.swiftdrop.integration.assistant.controller.AssistantController;
import com.swiftdrop.integration.assistant.dto.ChatMessageRequest;
import com.swiftdrop.integration.assistant.dto.ChatMessageResponse;
import com.swiftdrop.integration.assistant.service.AssistantRateLimiter;
import com.swiftdrop.integration.assistant.service.AssistantService;
import com.swiftdrop.integration.assistant.tools.AssistantTools;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class AssistantControllerTest {

    private AssistantTools tools;
    private AssistantController controller;
    private Jwt customerJwt;

    @BeforeEach
    void setUp() {
        tools = mock(AssistantTools.class);
        controller = new AssistantController(new AssistantService(new FakeLlmClient(), tools), unlimited());
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
    void rejectsIrrelevantQuestionWithoutCallingTools() {
        ChatMessageResponse response = controller.chat(
                new ChatMessageRequest("what is my name", null), customerJwt, null).getBody();

        assertTrue(response.reply().contains("SwiftDrop parcels"));
        assertTrue(response.reply().contains("only answer questions regarding your own"));
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
                new AssistantController(new AssistantService(unavailable, tools), unlimited());
        when(tools.getCustomerParcels(null, 0, 20, "customer-token")).thenReturn(List.of());

        ChatMessageResponse response = unavailableController.chat(
                new ChatMessageRequest("Show my recent parcels", null), customerJwt, null).getBody();

        assertTrue(response.reply().contains("temporarily unavailable"));
        assertTrue(!response.reply().contains("provider secret"));
    }

    @Test
    void handlesNaturalLanguageFeeQuestionWithDefaultMediumSize() {
        when(tools.estimateFee("Colombo", "Kandy", 2.0, "MEDIUM", "customer-token"))
                .thenReturn(new AssistantTools.FeeEstimateResult(2.0, "MEDIUM", "Kandy",
                        java.math.BigDecimal.valueOf(650), "LKR"));

        ChatMessageResponse response = controller.chat(
                new ChatMessageRequest("How much to send 2 kg from Colombo to Kandy?", null),
                customerJwt, null).getBody();

        assertTrue(response.reply().contains("650"));
        assertTrue(!response.reply().contains("FeeEstimateResult"));
        verify(tools).estimateFee("Colombo", "Kandy", 2.0, "MEDIUM", "customer-token");
    }

    @Test
    void formatsCustomerParcelListCleanlyWithoutRawRecordSyntax() {
        when(tools.getCustomerParcels(null, 0, 20, "customer-token")).thenReturn(List.of(
                new AssistantTools.ParcelLookupResult("SD-20261114-LCFZKK", "CREATED", "Colombo", false,
                        Instant.parse("2026-11-14T13:12:56Z"), java.math.BigDecimal.valueOf(700)),
                new AssistantTools.ParcelLookupResult("SD-20261114-BEK6JR", "CANCELLED", null, false,
                        Instant.parse("2026-11-14T13:12:32Z"), java.math.BigDecimal.valueOf(1600))));

        ChatMessageResponse response = controller.chat(
                new ChatMessageRequest("What are my all shipments?", null), customerJwt, null).getBody();

        assertTrue(response.reply().contains("SD-20261114-LCFZKK"));
        assertTrue(response.reply().contains("CREATED"));
        assertTrue(response.reply().contains("SD-20261114-BEK6JR"));
        assertTrue(response.reply().contains("CANCELLED"));
        assertTrue(!response.reply().contains("ParcelLookupResult"));
    }

    @Test
    void rejectsMessagesOverThePerUserRateLimitWithoutCallingTools() {
        AssistantController limited = new AssistantController(
                new AssistantService(new FakeLlmClient(), tools),
                new AssistantRateLimiter(2, Duration.ofMinutes(1), Clock.systemUTC()));
        ChatMessageRequest request = new ChatMessageRequest("hello", null);

        limited.chat(request, customerJwt, null);
        limited.chat(request, customerJwt, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> limited.chat(new ChatMessageRequest("Where is parcel SD-20261024-ABC123?", null), customerJwt, null));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        verifyNoInteractions(tools);
    }

    private static AssistantRateLimiter unlimited() {
        return new AssistantRateLimiter(Integer.MAX_VALUE, Duration.ofMinutes(1), Clock.systemUTC());
    }
}
