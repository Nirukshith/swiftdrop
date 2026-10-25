package com.swiftdrop.integration.assistant.controller;

import com.swiftdrop.integration.assistant.client.LlmClient;
import com.swiftdrop.integration.assistant.dto.ChatMessageRequest;
import com.swiftdrop.integration.assistant.dto.ChatMessageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for customer interactions with the AI Delivery Assistant.
 * Validates input parameters (length <= 500 chars) and passes caller's token to tools.
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final LlmClient llmClient;

    public AssistantController(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatMessageResponse> chat(
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String conversationId = (request.conversationId() != null && !request.conversationId().isBlank())
                ? request.conversationId()
                : "conv-" + UUID.randomUUID().toString().substring(0, 8);

        String token = "";
        if (jwt != null) {
            token = jwt.getTokenValue();
        } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        String reply = llmClient.chat(request.message(), conversationId, token);

        return ResponseEntity.ok(new ChatMessageResponse(reply, conversationId));
    }
}
