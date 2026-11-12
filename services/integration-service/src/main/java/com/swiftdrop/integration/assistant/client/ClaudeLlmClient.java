package com.swiftdrop.integration.assistant.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;

/**
 * Hosted Claude API implementation supporting tool calling.
 * Provider is activated when `swiftdrop.assistant.llm-provider=claude`.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.assistant.llm-provider", havingValue = "claude")
public class ClaudeLlmClient implements LlmClient {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    public ClaudeLlmClient(
            @Value("${LLM_API_KEY:}") String apiKey,
            @Value("${swiftdrop.assistant.model:claude-3-5-haiku-latest}") String model,
            @Value("${swiftdrop.assistant.provider-url:https://api.anthropic.com}") String providerUrl,
            @Value("${swiftdrop.assistant.provider-timeout:PT10S}") Duration timeout) {
        this.apiKey = apiKey;
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().baseUrl(providerUrl).requestFactory(factory).build();
    }

    @Override
    public String chat(String prompt, String conversationId, String callerToken) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY environment variable is not configured");
        }
        try {
            var body = restClient.post().uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .body(java.util.Map.of(
                            "model", model,
                            "max_tokens", 400,
                            "messages", java.util.List.of(java.util.Map.of("role", "user", "content", prompt))))
                    .retrieve()
                    .body(ClaudeResponse.class);
            if (body == null || body.content() == null || body.content().isEmpty()) {
                throw new IllegalStateException("LLM provider returned no response");
            }
            return body.content().get(0).text();
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new IllegalStateException("LLM provider is unavailable", ex);
        }
    }

    private record ClaudeResponse(java.util.List<Content> content) {}
    private record Content(String text) {}
}
