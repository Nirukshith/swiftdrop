package com.swiftdrop.auth.dto;

public record UserSummary(
    String id,
    String email,
    String fullName,
    String role,
    String merchantId
) {}
