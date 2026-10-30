package com.swiftdrop.auth.internal.dto;

public record UserContactResponse(
    String userId,
    String fullName,
    String email,
    String phoneNumber,
    String role
) {}
