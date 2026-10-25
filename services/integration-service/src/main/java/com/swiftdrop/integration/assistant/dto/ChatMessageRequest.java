package com.swiftdrop.integration.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
    @NotBlank(message = "Message cannot be blank")
    @Size(max = 500, message = "Message exceeds maximum length of 500 characters")
    String message,
    String conversationId
) {}
