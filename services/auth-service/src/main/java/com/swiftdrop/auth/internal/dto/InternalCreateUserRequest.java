package com.swiftdrop.auth.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InternalCreateUserRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    String password,

    @NotBlank(message = "Full name is required")
    String fullName,

    String phoneNumber,

    @NotBlank(message = "Role is required")
    String role,

    String merchantId,

    Boolean enabled
) {}
