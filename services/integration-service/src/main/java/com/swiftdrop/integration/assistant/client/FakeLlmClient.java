package com.swiftdrop.integration.assistant.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fake LLM client providing deterministic, canned responses for unit/integration tests
 * and local demonstrations without requiring external API keys.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.assistant.llm-provider", havingValue = "fake", matchIfMissing = true)
public class FakeLlmClient implements LlmClient {

    @Override
    public String chat(String prompt, String conversationId, String callerToken) {
        String lower = prompt != null ? prompt.toLowerCase() : "";

        if (lower.contains("kandy") || lower.contains("fee") || lower.contains("how much") || lower.contains("quote")) {
            return "The estimated delivery fee for sending 2 kg to Kandy is 650.00 LKR.";
        }
        if (lower.contains("where") || lower.contains("parcel") || lower.contains("status") || lower.contains("track")) {
            return "Your parcel SD-20261024-001 is currently OUT_FOR_DELIVERY with courier Nuwan.";
        }
        if (lower.contains("recent") || lower.contains("history") || lower.contains("orders")) {
            return "You have 1 recent parcel: SD-20261024-001, currently OUT_FOR_DELIVERY.";
        }

        return "Hello! I am your SwiftDrop Delivery Assistant. I can help answer 'Where is my parcel?', estimate delivery fees, or list your recent orders.";
    }
}
