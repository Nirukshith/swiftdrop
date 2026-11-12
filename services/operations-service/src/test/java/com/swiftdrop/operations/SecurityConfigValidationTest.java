package com.swiftdrop.operations;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigValidationTest {

    private static final String ISSUER = "https://auth.swiftdrop.local";
    private static final String AUDIENCE = "swiftdrop-api";

    @Test
    void acceptsTokenWithConfiguredIssuerAudienceAndExpiry() {
        Instant now = Instant.now();
        OAuth2TokenValidatorResult result = SecurityConfig.tokenValidator(ISSUER, AUDIENCE)
                .validate(token(ISSUER, List.of(AUDIENCE), now.minusSeconds(60), now.plusSeconds(900)));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();
        OAuth2TokenValidatorResult result = SecurityConfig.tokenValidator(ISSUER, AUDIENCE)
                .validate(token(ISSUER, List.of(AUDIENCE), now.minusSeconds(600), now.minusSeconds(300)));

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithWrongIssuer() {
        Instant now = Instant.now();
        OAuth2TokenValidatorResult result = SecurityConfig.tokenValidator(ISSUER, AUDIENCE)
                .validate(token("https://untrusted.example", List.of(AUDIENCE),
                        now.minusSeconds(60), now.plusSeconds(900)));

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        Instant now = Instant.now();
        OAuth2TokenValidatorResult result = SecurityConfig.tokenValidator(ISSUER, AUDIENCE)
                .validate(token(ISSUER, List.of("other-api"), now.minusSeconds(60), now.plusSeconds(900)));

        assertThat(result.hasErrors()).isTrue();
    }

    private Jwt token(String issuer, List<String> audience, Instant issuedAt, Instant expiresAt) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(audience)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject("pricing-client")
                .build();
    }
}
