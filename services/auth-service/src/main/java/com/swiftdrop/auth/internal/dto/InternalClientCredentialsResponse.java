package com.swiftdrop.auth.internal.dto;

import java.time.Instant;
import java.util.List;

public record InternalClientCredentialsResponse(
    String clientId,
    String clientSecret,
    String merchantId,
    String clientName,
    List<String> scopes,
    Instant createdAt
) {}
