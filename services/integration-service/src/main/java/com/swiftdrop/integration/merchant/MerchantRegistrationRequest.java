package com.swiftdrop.integration.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MerchantRegistrationRequest(
        @NotBlank @Size(max = 200) String businessName,
        @NotBlank @Size(max = 200) String contactName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,14}$") String phoneNumber,
        @Size(max = 128) String businessRegistrationNumber) {}
