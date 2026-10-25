package com.swiftdrop.integration.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignerTest {

    private static final String SECRET = "whsec_test_secret_key_1234567890abcdef";
    private static final String SAMPLE_PAYLOAD = """
            {"deliveryId":"c1a8e9f2-2b3c-4d5e-8f9a-0b1c2d3e4f5a","eventType":"PARCEL_STATUS_CHANGED","sequence":3}
            """.trim();

    @Test
    @DisplayName("sign() generates valid signature header with timestamp and hex digest")
    void testSigningGeneratesValidHeader() {
        long timestamp = 1729864800L;
        String header = WebhookSigner.sign(SAMPLE_PAYLOAD, timestamp, SECRET);

        assertThat(header).startsWith("t=1729864800,v1=");
        String rawHex = header.substring(header.indexOf("v1=") + 3);
        assertThat(rawHex).hasSize(64); // SHA-256 is 32 bytes = 64 hex chars
    }

    @Test
    @DisplayName("verify() succeeds when payload, signature, and timestamp are authentic and within tolerance")
    void testVerificationSuccess() {
        long timestamp = 1729864800L;
        String header = WebhookSigner.sign(SAMPLE_PAYLOAD, timestamp, SECRET);

        boolean valid = WebhookSigner.verify(SAMPLE_PAYLOAD, header, SECRET, 300L, timestamp + 10L);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("verify() fails when body payload is tampered")
    void testTamperedBodyFails() {
        long timestamp = 1729864800L;
        String header = WebhookSigner.sign(SAMPLE_PAYLOAD, timestamp, SECRET);

        // Tamper by modifying one character in the payload
        String tamperedPayload = SAMPLE_PAYLOAD.replace("\"sequence\":3", "\"sequence\":4");

        boolean valid = WebhookSigner.verify(tamperedPayload, header, SECRET, 300L, timestamp + 10L);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("verify() fails when timestamp is expired (older than tolerance)")
    void testExpiredTimestampFails() {
        long timestamp = 1729864800L;
        String header = WebhookSigner.sign(SAMPLE_PAYLOAD, timestamp, SECRET);

        // Current time is 301 seconds later (tolerance is 300 seconds)
        long currentTime = timestamp + 301L;

        boolean valid = WebhookSigner.verify(SAMPLE_PAYLOAD, header, SECRET, 300L, currentTime);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("verify() fails when timestamp is too far in the future")
    void testFutureTimestampFails() {
        long timestamp = 1729864800L;
        String header = WebhookSigner.sign(SAMPLE_PAYLOAD, timestamp, SECRET);

        // Current time is 305 seconds before the event timestamp
        long currentTime = timestamp - 305L;

        boolean valid = WebhookSigner.verify(SAMPLE_PAYLOAD, header, SECRET, 300L, currentTime);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("verify() fails when signed with a different secret")
    void testDifferentSecretFails() {
        long timestamp = 1729864800L;
        String header = WebhookSigner.sign(SAMPLE_PAYLOAD, timestamp, SECRET);

        boolean valid = WebhookSigner.verify(SAMPLE_PAYLOAD, header, "different_secret_key_9999", 300L, timestamp);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("verify() fails gracefully on malformed signature header")
    void testMalformedHeaderFailsGracefully() {
        assertThat(WebhookSigner.verify(SAMPLE_PAYLOAD, "invalid_header", SECRET)).isFalse();
        assertThat(WebhookSigner.verify(SAMPLE_PAYLOAD, "t=not_a_number,v1=abcd", SECRET)).isFalse();
        assertThat(WebhookSigner.verify(SAMPLE_PAYLOAD, "t=1729864800", SECRET)).isFalse();
        assertThat(WebhookSigner.verify(SAMPLE_PAYLOAD, null, SECRET)).isFalse();
        assertThat(WebhookSigner.verify(SAMPLE_PAYLOAD, "", SECRET)).isFalse();
    }

    @Test
    @DisplayName("sign() throws exception when secret is null or blank")
    void testNullSecretThrows() {
        assertThatThrownBy(() -> WebhookSigner.sign(SAMPLE_PAYLOAD, 1729864800L, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> WebhookSigner.sign(SAMPLE_PAYLOAD, 1729864800L, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
