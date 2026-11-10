package com.swiftdrop.integration.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class HttpParcelClientTest {
    private final UUID merchantId = UUID.fromString("a1250d5e-2d60-4b8b-8fde-a3bf92b9c008");
    private final UUID parcelId = UUID.fromString("247f52b9-1658-4c06-a68c-a3c5405a0932");
    private MockRestServiceServer server;
    private HttpParcelClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://parcel-service:8082");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpParcelClient(builder.build());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUsesMergedInternalContractAndDerivedIdempotencyHeader() {
        StructuredAddress address = new StructuredAddress("10 Main Road", null, "Colombo", "Colombo", "00100");
        CreateMerchantOrderRequest request = new CreateMerchantOrderRequest("store-order-1", "Test Recipient",
                "+94771234567", address, address, MerchantParcelSize.SMALL, new BigDecimal("1.50"));
        server.expect(requestTo("http://parcel-service:8082/internal/v1/parcels"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "m6-derived-create-key"))
                .andExpect(content().json("""
                        {"merchantId":"%s","externalReference":"store-order-1",
                         "pickupAddress":{"line1":"10 Main Road","line2":null,"city":"Colombo","district":"Colombo","postalCode":"00100"},
                         "dropoffAddress":{"line1":"10 Main Road","line2":null,"city":"Colombo","district":"Colombo","postalCode":"00100"},
                         "weightKg":1.50,"size":"SMALL","fee":850.00,
                         "recipientName":"Test Recipient","recipientPhone":"+94771234567"}
                        """.formatted(merchantId), true))
                .andRespond(withSuccess(response("CREATED"), MediaType.APPLICATION_JSON));

        var result = client.createParcel(merchantId, request, new BigDecimal("850.00"), "m6-derived-create-key");
        assertThat(result.id()).isEqualTo(parcelId.toString());
        assertThat(result.merchantId()).isEqualTo(merchantId.toString());
        assertThat(result.fee()).isEqualByComparingTo("850.00");
        server.verify();
    }

    @Test
    void cancelUsesPatchWithExpectedCreatedStatusAndContractIdempotencyField() {
        server.expect(requestTo("http://parcel-service:8082/internal/v1/parcels/" + parcelId + "/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("""
                        {"status":"CANCELLED","expectedCurrentStatus":"CREATED",
                         "reason":"Duplicate order","idempotencyKey":"m6-derived-cancel-key"}
                        """, true))
                .andRespond(withSuccess(response("CANCELLED"), MediaType.APPLICATION_JSON));

        var result = client.cancelParcel(parcelId, "Duplicate order", "m6-derived-cancel-key");
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.updatedAt()).isNotNull();
        server.verify();
    }

    @Test
    void preservesParcelConflictForTheServiceToMapWithoutRetrying() {
        server.expect(requestTo("http://parcel-service:8082/internal/v1/parcels/" + parcelId + "/status"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.cancelParcel(parcelId, null, "cancel-key"))
                .isInstanceOf(HttpClientErrorException.Conflict.class);
        server.verify();
    }

    @Test
    void relaysTheValidatedJwtSoParcelErrorDispatchRetainsCallerAuthentication() {
        Jwt jwt = Jwt.withTokenValue("validated-test-token").header("alg", "RS256")
                .subject("client-1").claim("merchantId", merchantId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))));
        server.expect(requestTo("http://parcel-service:8082/internal/v1/parcels/" + parcelId + "/status"))
                .andExpect(header("Authorization", "Bearer validated-test-token"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.cancelParcel(parcelId, "Changed mind", "cancel-key"))
                .isInstanceOf(HttpClientErrorException.Conflict.class);
        server.verify();
    }

    @Test
    void neverTreatsOtherAuthenticationCredentialsAsABearerToken() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "not-a-jwt", java.util.List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))));
        server.expect(requestTo("http://parcel-service:8082/internal/v1/parcels/" + parcelId + "/status"))
                .andExpect(request -> assertThat(request.getHeaders().containsKey("Authorization")).isFalse())
                .andRespond(withSuccess(response("CANCELLED"), MediaType.APPLICATION_JSON));

        assertThat(client.cancelParcel(parcelId, null, "cancel-key").status()).isEqualTo("CANCELLED");
        server.verify();
    }

    private String response(String status) {
        return """
                {"id":"%s","trackingNumber":"SD-20261110-0001","status":"%s",
                 "merchantId":"%s","fee":850.00,"createdAt":"2026-11-10T00:00:00Z",
                 "updatedAt":"2026-11-10T00:05:00Z","customerId":null,"attemptCount":0}
                """.formatted(parcelId, status, merchantId);
    }
}
