package com.swiftdrop.integration.assistant;

import com.swiftdrop.integration.assistant.client.FakeLlmClient;
import com.swiftdrop.integration.assistant.client.LlmClient;
import com.swiftdrop.integration.assistant.controller.AssistantController;
import com.swiftdrop.integration.assistant.dto.ChatMessageRequest;
import com.swiftdrop.integration.assistant.dto.ChatMessageResponse;
import com.swiftdrop.integration.assistant.tools.AssistantTools;
import com.swiftdrop.integration.assistant.tools.AssistantToolsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class AssistantControllerTest {

    private LlmClient llmClient;
    private AssistantController controller;
    private AssistantTools tools;

    @BeforeEach
    void setUp() {
        llmClient = new FakeLlmClient();
        controller = new AssistantController(llmClient);
        tools = new AssistantToolsImpl();
    }

    @Test
    void testChatFeeEstimateQuestion() {
        ChatMessageRequest request = new ChatMessageRequest("How much to send 2 kg to Kandy?", null);
        ResponseEntity<ChatMessageResponse> response = controller.chat(request, null, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().reply().contains("650.00 LKR"));
        assertNotNull(response.getBody().conversationId());
    }

    @Test
    void testChatTrackingStatusQuestion() {
        ChatMessageRequest request = new ChatMessageRequest("Where is my parcel?", "conv-test-123");
        ResponseEntity<ChatMessageResponse> response = controller.chat(request, null, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("conv-test-123", response.getBody().conversationId());
        assertTrue(response.getBody().reply().contains("OUT_FOR_DELIVERY"));
    }

    @Test
    void testAssistantToolsSkeleton() {
        var status = tools.getParcelStatus("SD-20261024-001", "mock-token");
        assertEquals("SD-20261024-001", status.trackingNumber());
        assertEquals("OUT_FOR_DELIVERY", status.status());

        var fee = tools.estimateFee(2.0, "MEDIUM", "Kandy", "mock-token");
        assertEquals("Kandy", fee.destinationDistrict());
        assertNotNull(fee.estimatedFee());

        var recent = tools.listMyRecentParcels("mock-token");
        assertNotNull(recent);
        assertFalse(recent.isEmpty());
    }
}
