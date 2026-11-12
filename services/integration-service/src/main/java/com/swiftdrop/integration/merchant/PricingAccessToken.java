package com.swiftdrop.integration.merchant;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Dedicated pricing credentials; merchant bearer tokens are never used for pricing. */
@Component
public class PricingAccessToken {
    private final RestClient auth;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private String token;
    private Instant expiresAt = Instant.MIN;

    @Autowired
    public PricingAccessToken(
            @Value("${services.auth.base-url:${swiftdrop.auth.base-url:http://localhost:8081}}") String authUrl,
            @Value("${merchant.pricing.client-id:}") String clientId,
            @Value("${merchant.pricing.client-secret:}") String clientSecret) {
        this(RestClient.builder().baseUrl(authUrl).build(), clientId, clientSecret, Clock.systemUTC());
    }

    PricingAccessToken(RestClient auth, String clientId, String clientSecret, Clock clock) {
        this.auth = auth;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    public synchronized String get() {
        if (token != null && clock.instant().isBefore(expiresAt)) return token;
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("Merchant pricing service credentials are not configured");
        }
        // Include exchange latency in the lifetime; refresh early and retain tokens only in memory.
        Instant requestedAt = clock.instant();
        TokenResponse response;
        try {
            response = auth.post().uri("/api/v1/auth/token")
                    .body(Map.of("grant_type", "client_credentials", "client_id", clientId,
                            "client_secret", clientSecret, "scope", "pricing:read"))
                    .retrieve().body(TokenResponse.class);
        } catch (RestClientException exception) {
            // Do not propagate an upstream response body that might contain credentials.
            throw new IllegalStateException("Pricing service token exchange failed");
        }
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()
                || !"Bearer".equalsIgnoreCase(response.tokenType()) || response.expiresIn() <= 0
                || (response.scope() != null
                    && !Arrays.asList(response.scope().split("\\s+")).contains("pricing:read"))) {
            throw new IllegalStateException("Auth service returned an invalid pricing token response");
        }
        expiresAt = requestedAt.plusSeconds(Math.max(0, Math.min(response.expiresIn(), 900) - 30));
        token = response.accessToken();
        return token;
    }

    public synchronized void invalidate(String rejectedToken) {
        if (rejectedToken.equals(token)) {
            token = null;
            expiresAt = Instant.MIN;
        }
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("token_type") String tokenType,
                                 @JsonProperty("expires_in") long expiresIn, String scope) {}
}
