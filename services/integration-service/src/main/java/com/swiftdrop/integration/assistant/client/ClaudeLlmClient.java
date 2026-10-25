package com.swiftdrop.integration.assistant.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Hosted Claude API implementation supporting tool calling.
 * Provider is activated when `swiftdrop.assistant.llm-provider=claude`.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.assistant.llm-provider", havingValue = "claude")
public class ClaudeLlmClient implements LlmClient {

    private final String apiKey;

    public ClaudeLlmClient(@Value("${LLM_API_KEY:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String chat(String prompt, String conversationId, String callerToken) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY environment variable is not configured");
        }
        // Week 1 Skeleton: In week 4 integration phase, invokes Anthropic Claude Messages API
        return "Claude Assistant skeleton response for query: " + prompt;
    }
}
