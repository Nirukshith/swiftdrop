package com.swiftdrop.integration.merchant;

import java.time.Instant;
import java.util.UUID;

public record MerchantSummary(UUID id, String businessName, String contactName, String email,
                              String phoneNumber, MerchantStatus status, String rejectionReason,
                              Instant createdAt, boolean credentialRecoveryAvailable) {
    public MerchantSummary(UUID id, String businessName, String contactName, String email,
                           String phoneNumber, MerchantStatus status, String rejectionReason, Instant createdAt) {
        this(id, businessName, contactName, email, phoneNumber, status, rejectionReason, createdAt, false);
    }
    public static MerchantSummary from(Merchant merchant) {
        return new MerchantSummary(merchant.getId(), merchant.getBusinessName(), merchant.getContactName(),
                merchant.getEmail(), merchant.getPhoneNumber(), merchant.getStatus(),
                merchant.getRejectionReason(), merchant.getCreatedAt(), merchant.isCredentialRecoveryAvailable());
    }
}
