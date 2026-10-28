package com.swiftdrop.operations.dispatch.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component("dispatchRestParcelServiceClient")
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
    public ParcelPageClientResponse queryUnassignedParcels(String zone, LocalDate date, int page, int size) {
        return restClient.get()
            .uri(uriBuilder -> {
                uriBuilder.path("/internal/v1/parcels")
                    .queryParam("unassigned", "true")
                    .queryParam("page", page)
                    .queryParam("size", size);
                if (zone != null && !zone.isBlank()) {
                    uriBuilder.queryParam("zone", zone);
                }
                if (date != null) {
                    uriBuilder.queryParam("date", date.format(DateTimeFormatter.ISO_DATE));
                }
                return uriBuilder.build();
            })
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (req, resp) -> {
                throw new ParcelNotFoundException("Parcels query returned not found");
            })
            .onStatus(status -> status.isError(), (req, resp) -> {
                throw new ResponseStatusException(resp.getStatusCode(), "Failed to query unassigned parcels: " + resp.getStatusText());
            })
            .body(ParcelPageClientResponse.class);
    }

    @Override
    public ParcelClientResponse assignCourier(String parcelId, String courierId, String assignedBy) {
        return restClient.put()
            .uri("/internal/v1/parcels/{id}/assignment", parcelId)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(new ParcelAssignmentClientRequest(courierId, assignedBy))
            .retrieve()
            .onStatus(status -> status.isSameCodeAs(HttpStatus.CONFLICT), (req, resp) -> {
                throw new ParcelAlreadyAssignedException("Parcel has already been assigned to another courier.");
            })
            .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (req, resp) -> {
                throw new ParcelNotFoundException("Parcel not found with id: " + parcelId);
            })
            .onStatus(status -> status.isError(), (req, resp) -> {
                throw new ResponseStatusException(resp.getStatusCode(), "Assignment failed: " + resp.getStatusText());
            })
            .body(ParcelClientResponse.class);
    }
}
