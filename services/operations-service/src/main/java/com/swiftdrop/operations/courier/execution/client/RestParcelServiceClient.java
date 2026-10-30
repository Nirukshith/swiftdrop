package com.swiftdrop.operations.courier.execution.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Component
public class RestParcelServiceClient implements ParcelServiceClient {

    private final RestClient restClient;

    public RestParcelServiceClient(
        RestClient.Builder restClientBuilder,
        @Value("${swiftdrop.services.parcel-service.url:http://localhost:8082}") String parcelServiceUrl
    ) {
        this.restClient = restClientBuilder
            .baseUrl(parcelServiceUrl)
            .build();
    }

    @Override
    public ParcelPageResponse getParcelsByCourierAndDate(String courierId, String date) {
        return restClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path("/internal/v1/parcels")
                    .queryParam("courierId", courierId);
                if (date != null && !date.isBlank()) {
                    uriBuilder.queryParam("date", date);
                }
                return uriBuilder.build();
            })
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, resp) -> {
                throw new ResponseStatusException(resp.getStatusCode(), "Failed to query parcels for courier: " + resp.getStatusText());
            })
            .body(ParcelPageResponse.class);
    }

    @Override
    public ParcelResponse getParcel(UUID parcelId) {
        return restClient.get()
            .uri("/api/v1/parcels/{id}", parcelId)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, resp) -> {
                throw new ResponseStatusException(resp.getStatusCode(), "Parcel not found with ID: " + parcelId);
            })
            .body(ParcelResponse.class);
    }

    @Override
    public ParcelResponse updateStatus(UUID parcelId, ParcelStatusUpdateRequest request) {
        return restClient.patch()
            .uri("/internal/v1/parcels/{id}/status", parcelId)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, resp) -> {
                throw new ResponseStatusException(resp.getStatusCode(), "Status update failed in parcel-service: " + resp.getStatusText());
            })
            .body(ParcelResponse.class);
    }
}
