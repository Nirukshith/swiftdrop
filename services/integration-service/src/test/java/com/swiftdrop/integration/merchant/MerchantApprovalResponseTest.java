package com.swiftdrop.integration.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MerchantApprovalResponseTest {
    @Test
    void diagnosticOutputNeverContainsTheOneTimeSecret() {
        MerchantApprovalResponse response = new MerchantApprovalResponse(null, "client-1", "private-test-secret");

        assertThat(response.toString()).contains("client-1", "[REDACTED]")
                .doesNotContain("private-test-secret");
    }
}
