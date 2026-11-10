package com.swiftdrop.integration.merchant;

public record MerchantApprovalResponse(MerchantSummary merchant, String clientId, String clientSecret) {
    @Override
    public String toString() {
        return "MerchantApprovalResponse[merchant=" + merchant + ", clientId=" + clientId
                + ", clientSecret=[REDACTED]]";
    }
}
