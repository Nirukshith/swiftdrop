package com.swiftdrop.integration.assistant.tools;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ParcelServiceClient {

    private final RestClient restClient;

    @Autowired
    public ParcelServiceClient(
            @Value("${services.parcel.base-url:http://localhost:8082}") String baseUrl,
            @Value("${swiftdrop.assistant.upstream-timeout:PT3S}") Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public ParcelServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ParcelPage list(String status, int page, int size, String token) {
        return restClient.get()
                .uri(uri -> {
                    var builder = uri.path("/api/v1/parcels")
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (status != null && !status.isBlank()) {
                        builder.queryParam("status", status);
                    }
                    return builder.build();
                })
                .headers(headers -> bearer(headers, token))
                .retrieve()
                .body(ParcelPage.class);
    }

    public ParcelDetail detail(String id, String token) {
        return restClient.get()
                .uri("/api/v1/parcels/{id}", id)
                .headers(headers -> bearer(headers, token))
                .retrieve()
                .body(ParcelDetail.class);
    }

    public ParcelHistory history(String id, String token) {
        return restClient.get()
                .uri("/api/v1/parcels/{id}/history", id)
                .headers(headers -> bearer(headers, token))
                .retrieve()
                .body(ParcelHistory.class);
    }

    public Quote quote(String pickupDistrict, String destinationDistrict, double weightKg, String size, String token) {
        return restClient.get()
                .uri(uri -> uri.path("/api/v1/parcels/quote")
                        .queryParam("pickupDistrict", pickupDistrict)
                        .queryParam("dropoffDistrict", destinationDistrict)
                        .queryParam("weightKg", weightKg)
                        .queryParam("size", size)
                        .build())
                .headers(headers -> bearer(headers, token))
                .retrieve()
                .body(Quote.class);
    }

    private static void bearer(HttpHeaders headers, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authenticated caller token is required");
        }
        headers.setBearerAuth(token);
    }

    public record ParcelPage(List<ParcelListItem> content, long totalElements, int totalPages, int page, int size) {
    }

    public record ParcelListItem(String id, String trackingNumber, String status, String courierId,
                          Instant createdAt, Instant updatedAt, BigDecimal fee) {
    }

    public record ParcelDetail(String id, String trackingNumber, String status, String courierId,
                        Address dropoffAddress, Instant createdAt, Instant updatedAt, BigDecimal fee) {
    }

    public record Address(String line1, String line2, String city, String district, String postalCode) {
    }

    public record ParcelHistory(String trackingNumber, String currentStatus, List<HistoryItem> entries) {
    }

    public record HistoryItem(String toStatus, Instant occurredAt) {
    }

    public record Quote(BigDecimal totalFee, String currency) {
    }
}
