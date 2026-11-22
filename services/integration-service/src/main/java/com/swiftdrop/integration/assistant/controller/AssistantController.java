package com.swiftdrop.integration.assistant.controller;

import com.swiftdrop.integration.assistant.dto.ChatMessageRequest;
import com.swiftdrop.integration.assistant.dto.ChatMessageResponse;
import com.swiftdrop.integration.assistant.service.AssistantRateLimiter;
import com.swiftdrop.integration.assistant.service.AssistantService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for customer interactions with the AI Delivery Assistant.
 * Validates input parameters (length <= 500 chars), enforces a per-user rate limit (429),
 * and passes caller's token to tools.
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final AssistantRateLimiter rateLimiter;

    public AssistantController(AssistantService assistantService, AssistantRateLimiter rateLimiter) {
        this.assistantService = assistantService;
        this.rateLimiter = rateLimiter;
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

        if (token.isBlank()) {
            throw new IllegalStateException("Authenticated customer token is required");
        }

        String rateLimitKey = (jwt != null && jwt.getSubject() != null) ? jwt.getSubject() : token;
        if (!rateLimiter.tryAcquire(rateLimitKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many assistant messages. Please wait a minute and try again.");
        }
        String reply = assistantService.answer(request.message(), conversationId, token);

        return ResponseEntity.ok(new ChatMessageResponse(reply, conversationId));
    }
}
