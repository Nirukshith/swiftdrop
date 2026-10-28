package com.swiftdrop.integration.notification.dto;

public record UserContactDto(
    String userId,
    String fullName,
    String email,
    String phoneNumber,
    String role
) {}

