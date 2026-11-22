package com.swiftdrop.integration.assistant.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Hosted Claude API implementation (Messages API over REST).
 * Provider is activated when `swiftdrop.assistant.llm-provider=claude`.
 *
 * <p>Current Claude models think by default, so a response can contain thinking blocks
 * before the answer. Only {@code text} blocks are returned to the customer.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.assistant.llm-provider", havingValue = "claude")
public class ClaudeLlmClient implements LlmClient {

    static final String DEFAULT_MODEL = "claude-opus-5-5";
    static final String REFUSAL_MESSAGE =
            "I'm sorry, I can't help with that request. I can answer questions about your SwiftDrop "
                    + "parcels, tracking, and delivery fees.";

    private static final String API_VERSION = "2023-06-01";
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    // Thinking tokens count toward max_tokens; a small cap could be spent before any answer is written.
    private static final int MAX_TOKENS = 16000;
    // Short, fact-grounded chat answers do not need deep reasoning; low effort keeps latency and cost down.
    private static final String EFFORT = "low";

    private final String apiKey;
    private final String model;
    private final boolean refusalFallback;
    private final RestClient restClient;

    public ClaudeLlmClient(
            @Value("${LLM_API_KEY:}") String apiKey,
            @Value("${swiftdrop.assistant.model:" + DEFAULT_MODEL + "}") String model,
            @Value("${swiftdrop.assistant.provider-url:https://api.anthropic.com}") String providerUrl,
            @Value("${swiftdrop.assistant.provider-timeout:PT30S}") Duration timeout,
            @Value("${swiftdrop.assistant.refusal-fallback:true}") boolean refusalFallback) {
        this.apiKey = apiKey;
        this.model = model;
        // Server-side refusal fallback is not available on Haiku models.
        this.refusalFallback = refusalFallback && !model.startsWith("claude-haiku");
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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("output_config", Map.of("effort", EFFORT));
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        if (refusalFallback) {
            body.put("fallbacks", "default");
        }

        ClaudeResponse response;
        try {
            RestClient.RequestBodySpec request = restClient.post().uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION);
            if (refusalFallback) {
                request = request.header("anthropic-beta", FALLBACK_BETA);
            }
            response = request.body(body).retrieve().body(ClaudeResponse.class);
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new IllegalStateException("LLM provider is unavailable", ex);
        }

        if (response == null) {
            throw new IllegalStateException("LLM provider returned no response");
        }
        if ("refusal".equals(response.stopReason())) {
            return REFUSAL_MESSAGE;
        }
        String answer = response.content() == null ? "" : response.content().stream()
                .filter(Objects::nonNull)
                .filter(block -> "text".equals(block.type()))
                .map(Content::text)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
        if (answer.isBlank()) {
            throw new IllegalStateException(
                    "LLM provider returned no answer text (stop_reason=" + response.stopReason() + ")");
        }
        return answer;
    }

    private record ClaudeResponse(List<Content> content, @JsonProperty("stop_reason") String stopReason) {}

    private record Content(String type, String text) {}
}
