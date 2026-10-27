package com.swiftdrop.integration.merchant;

import java.time.Instant;
import java.util.UUID;

public record MerchantSummary(UUID id, String businessName, String contactName, String email,
                              String phoneNumber, MerchantStatus status, String rejectionReason,
                              Instant createdAt) {
    public static MerchantSummary from(Merchant merchant) {
        return new MerchantSummary(merchant.getId(), merchant.getBusinessName(), merchant.getContactName(),
                merchant.getEmail(), merchant.getPhoneNumber(), merchant.getStatus(),
                merchant.getRejectionReason(), merchant.getCreatedAt());
    }
}
