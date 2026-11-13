package com.swiftdrop.integration.merchant;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PricingAccessTokenTest {
    private MockRestServiceServer server;
    private PricingAccessToken tokens;
    private final Clock clock = org.mockito.Mockito.mock(Clock.class);
    private final Instant now = Instant.parse("2026-11-13T00:00:00Z");

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://auth");
        server = MockRestServiceServer.bindTo(builder).build();
        org.mockito.Mockito.when(clock.instant()).thenReturn(now);
        tokens = new PricingAccessToken(builder.build(), "pricing-client", "test-secret", clock);
    }

    @Test void exchangesOnlyDedicatedCredentialsAndRefreshesBeforeExpiry() {
        exchange("first", 900);
        exchange("second", 900);
        assertThat(tokens.get()).isEqualTo("first");
        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(869));
        assertThat(tokens.get()).isEqualTo("first");
        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(870));
        assertThat(tokens.get()).isEqualTo("second");
        server.verify();
    }

    @Test void invalidationCannotDiscardANewerToken() {
        exchange("first", 900);
        exchange("second", 900);
        assertThat(tokens.get()).isEqualTo("first");
        tokens.invalidate("first");
        assertThat(tokens.get()).isEqualTo("second");
        tokens.invalidate("first");
        assertThat(tokens.get()).isEqualTo("second");
        server.verify();
    }

    @Test void missingCredentialsFailWithoutAnHttpRequest() {
        tokens = new PricingAccessToken(RestClient.create("http://127.0.0.1:9"), "", "", Clock.systemUTC());
        assertThat(tokens.hasConfiguration()).isFalse();
        assertThatThrownBy(tokens::get).isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");
    }

    @Test void partialOAuthConfigurationRemainsSelectedAndFailsClosed() {
        tokens = new PricingAccessToken(RestClient.create("http://127.0.0.1:9"), "configured-client", "", Clock.systemUTC());
        assertThat(tokens.hasConfiguration()).isTrue();
        assertThatThrownBy(tokens::get).hasMessageContaining("not configured");
    }

    @Test void acceptsTheContractsOptionalScopeField() {
        server.expect(requestTo("http://auth/api/v1/auth/token"))
                .andExpect(content().json("{\"scope\":\"pricing:read\"}"))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"token_type\":\"Bearer\",\"expires_in\":900}", MediaType.APPLICATION_JSON));
        assertThat(tokens.get()).isEqualTo("token");
        server.verify();
    }

    @Test void failedExchangeIsSanitizedAndNextExplicitAttemptCanRecover() {
        server.expect(requestTo("http://auth/api/v1/auth/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("sensitive-upstream-body"));
        exchange("recovered", 900);
        assertThatThrownBy(tokens::get).hasMessage("Pricing service token exchange failed").hasNoCause();
        assertThat(tokens.get()).isEqualTo("recovered");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"access_token\":\"t\",\"token_type\":\"Basic\",\"expires_in\":900,\"scope\":\"pricing:read\"}",
            "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":0,\"scope\":\"pricing:read\"}",
            "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":900,\"scope\":\"parcels:create\"}"})
    void refusesInvalidOrInsufficientTokenResponses(String body) {
        server.expect(requestTo("http://auth/api/v1/auth/token")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(tokens::get).hasMessageContaining("invalid pricing token response");
        server.verify();
    }

    private void exchange(String token, int seconds) {
        server.expect(requestTo("http://auth/api/v1/auth/token")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                    {"grant_type":"client_credentials","client_id":"pricing-client",
                     "client_secret":"test-secret","scope":"pricing:read"}
                    """, true))
                .andRespond(withSuccess("""
                    {"access_token":"%s","token_type":"Bearer","expires_in":%d,"scope":"pricing:read"}
                    """.formatted(token, seconds), MediaType.APPLICATION_JSON));
    }
}
