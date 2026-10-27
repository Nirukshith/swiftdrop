package com.swiftdrop.integration.merchant;

public record MerchantApprovalResponse(MerchantSummary merchant, String clientId, String clientSecret) {}
