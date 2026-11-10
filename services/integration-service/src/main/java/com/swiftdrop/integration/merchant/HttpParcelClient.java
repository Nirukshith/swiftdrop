package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpParcelClient implements ParcelClient {

    private final RestClient restClient;

    @Autowired
    public HttpParcelClient(@Value("${services.parcel.base-url:http://localhost:8082}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    HttpParcelClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public InternalParcelResponse createParcel(UUID merchantId, CreateMerchantOrderRequest request,
                                               BigDecimal fee, String downstreamIdempotencyKey) {
        InternalCreateParcelRequest parcelRequest = new InternalCreateParcelRequest(
                merchantId.toString(), request.externalReference(), request.pickupAddress(), request.dropoffAddress(),
                request.weightKg(), request.size(), fee, request.recipientName(), request.recipientPhone());
        return restClient.post()
                .uri("/internal/v1/parcels")
                .header("Idempotency-Key", downstreamIdempotencyKey)
                .headers(HttpParcelClient::relayAuthentication)
                .body(parcelRequest)
                .retrieve()
                .body(InternalParcelResponse.class);
    }

    @Override
    public InternalParcelResponse cancelParcel(UUID parcelId, String reason, String downstreamIdempotencyKey) {
        return restClient.patch()
                .uri("/internal/v1/parcels/{id}/status", parcelId)
                .headers(HttpParcelClient::relayAuthentication)
                .body(new InternalParcelStatusUpdateRequest("CANCELLED", "CREATED", reason,
                        downstreamIdempotencyKey))
                .retrieve()
                .body(InternalParcelResponse.class);
    }

    private static void relayAuthentication(HttpHeaders headers) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && jwt.isAuthenticated()) {
            headers.setBearerAuth(jwt.getToken().getTokenValue());
        }
    }

    private record InternalCreateParcelRequest(String merchantId, String externalReference,
                                               StructuredAddress pickupAddress, StructuredAddress dropoffAddress,
                                               BigDecimal weightKg, MerchantParcelSize size, BigDecimal fee,
                                               String recipientName, String recipientPhone) {}

    private record InternalParcelStatusUpdateRequest(String status, String expectedCurrentStatus,
                                                     String reason, String idempotencyKey) {}
}
