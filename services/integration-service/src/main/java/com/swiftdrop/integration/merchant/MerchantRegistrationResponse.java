package com.swiftdrop.integration.merchant;

import java.time.Instant;
import java.util.UUID;

public record MerchantRegistrationResponse(UUID id, String businessName, String contactName, String email,
                                           String phoneNumber, MerchantStatus status, String rejectionReason,
                                           Instant createdAt, String message) {
    public static MerchantRegistrationResponse from(Merchant merchant) {
        return new MerchantRegistrationResponse(merchant.getId(), merchant.getBusinessName(),
                merchant.getContactName(), merchant.getEmail(), merchant.getPhoneNumber(), merchant.getStatus(),
                merchant.getRejectionReason(), merchant.getCreatedAt(),
                "Registration submitted for administrator review.");
    }
}
