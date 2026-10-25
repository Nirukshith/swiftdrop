package com.swiftdrop.integration.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Pure-Java HMAC-SHA256 webhook signature generator and verification helper.
 *
 * <p>Signs outbound webhook requests using the scheme:
 * {@code CanonicalPayload = timestamp + "." + rawBody}
 * {@code Signature = Hex(HmacSHA256(secret, CanonicalPayload))}
 *
 * <p>Outbound headers:
 * <ul>
 *   <li>{@code X-SwiftDrop-Signature: t=<timestamp>,v1=<hex_signature>}</li>
 *   <li>{@code X-SwiftDrop-Timestamp: <timestamp>}</li>
 * </ul>
 *
 * <p>Owner: Member M7 (Integration Service - Webhooks)
 */
public final class WebhookSigner {

    public static final String ALGORITHM = "HmacSHA256";
    public static final long DEFAULT_TOLERANCE_SECONDS = 300L; // 5 minutes

    public static final String SIGNATURE_HEADER = "X-SwiftDrop-Signature";
    public static final String TIMESTAMP_HEADER = "X-SwiftDrop-Timestamp";
    public static final String DELIVERY_ID_HEADER = "X-SwiftDrop-Delivery-Id";

    private WebhookSigner() {
        // Utility class
    }

    /**
     * Signs the given payload with the secret at the specified timestamp.
     *
     * @param payload               the raw JSON payload body
     * @param timestampEpochSeconds the epoch seconds timestamp to include in the signature
     * @param secret                the merchant shared HMAC secret
     * @return the formatted signature header value (e.g. {@code t=1729864800,v1=4d7f508a...})
     */
    public static String sign(String payload, long timestampEpochSeconds, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Signing secret must not be null or blank");
        }
        String safePayload = (payload == null) ? "" : payload;
        String rawHex = computeRawSignature(safePayload, timestampEpochSeconds, secret);
        return "t=" + timestampEpochSeconds + ",v1=" + rawHex;
    }

    /**
     * Signs the given payload with the secret using the current system time.
     *
     * @param payload the raw JSON payload body
     * @param secret  the merchant shared HMAC secret
     * @return the formatted signature header value
     */
    public static String sign(String payload, String secret) {
        return sign(payload, Instant.now().getEpochSecond(), secret);
    }

    /**
     * Computes the raw lowercase hexadecimal HMAC-SHA256 signature.
     *
     * @param payload               the raw JSON payload body
     * @param timestampEpochSeconds timestamp included in the canonical payload
     * @param secret                merchant shared HMAC secret
     * @return lowercase hex string of the HMAC-SHA256 digest
     */
    public static String computeRawSignature(String payload, long timestampEpochSeconds, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Signing secret must not be null or blank");
        }
        String safePayload = (payload == null) ? "" : payload;
        String canonicalPayload = timestampEpochSeconds + "." + safePayload;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    /**
     * Verifies an incoming webhook signature using the default 300-second tolerance.
     *
     * @param payload         the raw received JSON payload body
     * @param signatureHeader the value of {@code X-SwiftDrop-Signature} (e.g. {@code t=...,v1=...})
     * @param secret          the merchant shared secret
     * @return true if the signature is authentic and within tolerance; false otherwise
     */
    public static boolean verify(String payload, String signatureHeader, String secret) {
        return verify(payload, signatureHeader, secret, DEFAULT_TOLERANCE_SECONDS, Instant.now().getEpochSecond());
    }

    /**
     * Verifies an incoming webhook signature with a custom tolerance in seconds.
     *
     * @param payload          the raw received JSON payload body
     * @param signatureHeader  the value of {@code X-SwiftDrop-Signature}
     * @param secret           the merchant shared secret
     * @param toleranceSeconds maximum allowed clock drift / age in seconds
     * @return true if valid and not expired; false otherwise
     */
    public static boolean verify(String payload, String signatureHeader, String secret, long toleranceSeconds) {
        return verify(payload, signatureHeader, secret, toleranceSeconds, Instant.now().getEpochSecond());
    }

    /**
     * Verifies an incoming webhook signature with custom tolerance and explicit reference time (useful for unit testing).
     *
     * @param payload             the raw received payload
     * @param signatureHeader     the {@code X-SwiftDrop-Signature} header
     * @param secret              the merchant shared secret
     * @param toleranceSeconds    maximum age/drift tolerance in seconds
     * @param currentEpochSeconds reference current epoch seconds
     * @return true if authentic and timestamp is within {@code currentEpochSeconds ± toleranceSeconds}
     */
    public static boolean verify(String payload, String signatureHeader, String secret,
                                 long toleranceSeconds, long currentEpochSeconds) {
        if (signatureHeader == null || signatureHeader.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }

        String safePayload = (payload == null) ? "" : payload;
        ParsedHeader parsed = parseSignatureHeader(signatureHeader);
        if (parsed == null) {
            return false;
        }

        // Replay defense: verify timestamp is within tolerance window
        long age = Math.abs(currentEpochSeconds - parsed.timestamp());
        if (age > toleranceSeconds) {
            return false;
        }

        // Compute expected signature
        String expectedHex;
        try {
            expectedHex = computeRawSignature(safePayload, parsed.timestamp(), secret);
        } catch (Exception e) {
            return false;
        }

        // Constant-time byte comparison against timing attacks
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                parsed.signatureHex().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Parses the signature header in the format {@code t=<timestamp>,v1=<hex>}.
     */
    private static ParsedHeader parseSignatureHeader(String header) {
        try {
            long timestamp = -1L;
            String signatureHex = null;

            String[] pairs = header.split(",");
            for (String pair : pairs) {
                String[] kv = pair.trim().split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                String key = kv[0].trim();
                String val = kv[1].trim();
                if ("t".equals(key)) {
                    timestamp = Long.parseLong(val);
                } else if ("v1".equals(key)) {
                    signatureHex = val;
                }
            }

            if (timestamp > 0 && signatureHex != null && !signatureHex.isBlank()) {
                return new ParsedHeader(timestamp, signatureHex);
            }
        } catch (Exception ignored) {
            // Malformed header
        }
        return null;
    }

    private record ParsedHeader(long timestamp, String signatureHex) {}
}
