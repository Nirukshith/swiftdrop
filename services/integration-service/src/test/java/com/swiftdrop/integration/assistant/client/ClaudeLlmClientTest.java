package com.swiftdrop.integration.assistant.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Messages API request/response handling against a local stub server (no real API calls).
 */
class ClaudeLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> betaHeader = new AtomicReference<>();
    private final AtomicReference<String> versionHeader = new AtomicReference<>();
    private final AtomicReference<String> responseJson = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            betaHeader.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            versionHeader.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            byte[] bytes = responseJson.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ClaudeLlmClient client(String model) {
        return new ClaudeLlmClient("test-key", model,
                "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(5), true);
    }

    @Test
    @DisplayName("Returns only the text blocks when a thinking block comes first")
    void skipsThinkingBlocks() {
        responseJson.set("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"thinking","thinking":"","signature":"sig"},
                            {"type":"text","text":"Your parcel is out for delivery."}],
                 "stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5}}
                """);

        String reply = client(ClaudeLlmClient.DEFAULT_MODEL).chat("Where is my parcel?", "conv-1", "token");

        assertThat(reply).isEqualTo("Your parcel is out for delivery.");
    }

    @Test
    @DisplayName("Sends the model, a large token cap, low effort and the refusal fallback, with no sampling params")
    void sendsExpectedRequest() throws Exception {
        responseJson.set("""
                {"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}
                """);

        client(ClaudeLlmClient.DEFAULT_MODEL).chat("hello", "conv-1", "token");

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(16000);
        assertThat(body.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(body.path("fallbacks").asText()).isEqualTo("default");
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.has("thinking")).isFalse();
        assertThat(betaHeader.get()).isEqualTo("server-side-fallback-2026-07-01");
        assertThat(versionHeader.get()).isEqualTo("2023-06-01");
    }

    @Test
    @DisplayName("Haiku models do not request the server-side refusal fallback")
    void haikuOmitsFallback() throws Exception {
        responseJson.set("""
                {"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}
                """);

        client("claude-haiku-5-5").chat("hello", "conv-1", "token");

        assertThat(objectMapper.readTree(requestBody.get()).has("fallbacks")).isFalse();
        assertThat(betaHeader.get()).isNull();
    }

    @Test
    @DisplayName("A refusal returns a safe fixed message")
    void refusalReturnsSafeMessage() {
        responseJson.set("""
                {"content":[],"stop_reason":"refusal","stop_details":{"type":"refusal","category":null}}
                """);

        assertThat(client(ClaudeLlmClient.DEFAULT_MODEL).chat("hello", "conv-1", "token"))
                .isEqualTo(ClaudeLlmClient.REFUSAL_MESSAGE);
    }

    @Test
    @DisplayName("A response without answer text is treated as unavailable, not returned as empty")
    void noTextIsAnError() {
        responseJson.set("""
                {"content":[{"type":"thinking","thinking":""}],"stop_reason":"max_tokens"}
                """);

        assertThatThrownBy(() -> client(ClaudeLlmClient.DEFAULT_MODEL).chat("hello", "conv-1", "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max_tokens");
    }

    @Test
    @DisplayName("A missing API key fails before any request is sent")
    void missingKeyFails() {
        ClaudeLlmClient noKey = new ClaudeLlmClient("", ClaudeLlmClient.DEFAULT_MODEL,
                "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(5), true);

        assertThatThrownBy(() -> noKey.chat("hello", "conv-1", "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_API_KEY");
        assertThat(requestBody.get()).isNull();
    }
}
