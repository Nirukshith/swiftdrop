package com.swiftdrop.integration.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final SsrfGuard ssrfGuard;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebhookDispatcher(SsrfGuard ssrfGuard, ObjectMapper objectMapper) {
        this.ssrfGuard = ssrfGuard;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DispatchResult dispatch(String destinationUrl, String secret, String deliveryId, Object payloadObj) {
        // 1. DNS resolution & SSRF validation at transmission time
        ssrfGuard.validateUrl(destinationUrl);

        // 2. Serialize payload
        String rawJson;
        if (payloadObj instanceof String str) {
            rawJson = str;
        } else {
            try {
                rawJson = objectMapper.writeValueAsString(payloadObj);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to serialize webhook payload to JSON", e);
            }
        }

        // 3. Cryptographic signature generation
        long timestamp = Instant.now().getEpochSecond();
        String signatureHeader = WebhookSigner.sign(rawJson, timestamp, secret);
        String timestampStr = String.valueOf(timestamp);

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        requestHeaders.put(WebhookSigner.SIGNATURE_HEADER, signatureHeader);
        requestHeaders.put(WebhookSigner.TIMESTAMP_HEADER, timestampStr);
        requestHeaders.put(WebhookSigner.DELIVERY_ID_HEADER, deliveryId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(destinationUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(WebhookSigner.SIGNATURE_HEADER, signatureHeader)
                .header(WebhookSigner.TIMESTAMP_HEADER, timestampStr)
                .header(WebhookSigner.DELIVERY_ID_HEADER, deliveryId)
                .POST(HttpRequest.BodyPublishers.ofString(rawJson, StandardCharsets.UTF_8))
                .build();

        long startTime = System.currentTimeMillis();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int latencyMs = (int) (System.currentTimeMillis() - startTime);

            int statusCode = response.statusCode();
            boolean success = statusCode >= 200 && statusCode < 300;

            Map<String, String> responseHeaders = new HashMap<>();
            response.headers().map().forEach((k, v) -> {
                if (!v.isEmpty()) {
                    responseHeaders.put(k, String.join(", ", v));
                }
            });

            String body = response.body();
            String bodySummary = (body != null && body.length() > 500) ? body.substring(0, 500) : body;

            return new DispatchResult(
                    success,
                    statusCode,
                    latencyMs,
                    requestHeaders,
                    responseHeaders,
                    body,
                    bodySummary,
                    success ? null : "Target returned HTTP " + statusCode,
                    signatureHeader,
                    timestampStr
            );
        } catch (IOException | InterruptedException e) {
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            log.warn("Webhook dispatch failed to {}: {}", destinationUrl, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new DispatchResult(
                    false,
                    0,
                    latencyMs,
                    requestHeaders,
                    Map.of(),
                    null,
                    null,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    signatureHeader,
                    timestampStr
            );
        }
    }

    public record DispatchResult(
            boolean success,
            int statusCode,
            int latencyMs,
            Map<String, String> requestHeaders,
            Map<String, String> responseHeaders,
            String responseBody,
            String responseBodySummary,
            String errorMessage,
            String signatureSent,
            String timestampSent
    ) {
    }
}
