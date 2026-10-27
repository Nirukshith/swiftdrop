package com.swiftdrop.auth.internal.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record InternalCreateClientRequest(
    @NotBlank(message = "Merchant ID is required")
    String merchantId,

    @NotBlank(message = "Client name is required")
    String clientName,

    List<String> scopes
) {}
