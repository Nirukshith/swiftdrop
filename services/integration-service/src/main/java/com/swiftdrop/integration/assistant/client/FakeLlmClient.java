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
        int factsStart = prompt == null ? -1 : prompt.indexOf("Retrieved facts: ");
        String facts = factsStart >= 0 ? prompt.substring(factsStart + "Retrieved facts: ".length()) : "";
        if (facts.isBlank()) {
            return "That information is unavailable from SwiftDrop.";
        }
        if (facts.contains("UNKNOWN")) {
            return "I need the pickup district and parcel size before I can calculate a fee.";
        }
        return "Here is the SwiftDrop information I found: " + facts;
    }
}
