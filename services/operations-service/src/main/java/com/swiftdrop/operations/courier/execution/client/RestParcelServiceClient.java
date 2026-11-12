package com.swiftdrop.operations.courier.execution.client;

import com.swiftdrop.common.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class RestParcelServiceClient implements ParcelServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RestParcelServiceClient.class);

    private final RestClient restClient;

    public RestParcelServiceClient(
        RestClient.Builder restClientBuilder,
        @Value("${swiftdrop.services.parcel-service.url:http://localhost:8082}") String parcelServiceUrl
    ) {
        this.restClient = restClientBuilder
            .baseUrl(parcelServiceUrl)
            .build();
    }

    private String getCorrelationId() {
        String cid = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return (cid != null && !cid.isBlank()) ? cid : UUID.randomUUID().toString();
    }

    @Override
    public ParcelPageResponse getParcelsByCourierAndDate(String courierId, String date) {
        try {
            return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/internal/v1/parcels")
                        .queryParam("courierId", courierId)
                        .queryParam("size", 100);
                    if (date != null && !date.isBlank()) {
                        uriBuilder.queryParam("date", date);
                    }
                    return uriBuilder.build();
                })
                .accept(MediaType.APPLICATION_JSON)
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, getCorrelationId())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    String errorMsg = extractErrorMessage(resp, "Failed to query parcels for courier: " + resp.getStatusText());
                    throw new ResponseStatusException(resp.getStatusCode(), errorMsg);
                })
                .body(ParcelPageResponse.class);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (ResourceAccessException rae) {
            log.error("Network failure connecting to parcel-service for courier {}: {}", courierId, rae.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Parcel service is currently unreachable", rae);
        }
    }

    @Override
    public ParcelResponse getParcel(UUID parcelId) {
        try {
            return restClient.get()
                .uri("/internal/v1/parcels/{id}", parcelId)
                .accept(MediaType.APPLICATION_JSON)
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, getCorrelationId())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new ResponseStatusException(resp.getStatusCode(), "Parcel not found with ID: " + parcelId);
                })
                .body(ParcelResponse.class);
        } catch (ResponseStatusException rse) {
            if (rse.getStatusCode().value() == 404 || rse.getStatusCode().value() == 405) {
                return fetchFromPublicEndpoint(parcelId);
            }
            throw rse;
        } catch (ResourceAccessException rae) {
            log.error("Network failure fetching parcel {} from parcel-service: {}", parcelId, rae.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Parcel service is currently unreachable", rae);
        } catch (Exception e) {
            return fetchFromPublicEndpoint(parcelId);
        }
    }

    private ParcelResponse fetchFromPublicEndpoint(UUID parcelId) {
        try {
            return restClient.get()
                .uri("/api/v1/parcels/{id}", parcelId)
                .accept(MediaType.APPLICATION_JSON)
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, getCorrelationId())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    String errorMsg = extractErrorMessage(resp, "Parcel not found with ID: " + parcelId);
                    throw new ResponseStatusException(resp.getStatusCode(), errorMsg);
                })
                .body(ParcelResponse.class);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (ResourceAccessException rae) {
            log.error("Network failure fetching parcel {} from public endpoint: {}", parcelId, rae.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Parcel service is currently unreachable", rae);
        }
    }

    @Override
    public ParcelResponse updateStatus(UUID parcelId, ParcelStatusUpdateRequest request) {
        try {
            return restClient.patch()
                .uri("/internal/v1/parcels/{id}/status", parcelId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, getCorrelationId())
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    String errorMsg = extractErrorMessage(resp, "Status update failed in parcel-service: " + resp.getStatusText());
                    throw new ResponseStatusException(resp.getStatusCode(), errorMsg);
                })
                .body(ParcelResponse.class);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (ResourceAccessException rae) {
            log.error("Network failure updating parcel {} status in parcel-service: {}", parcelId, rae.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Parcel service is currently unreachable", rae);
        }
    }

    private String extractErrorMessage(ClientHttpResponse resp, String defaultMsg) {
        try {
            byte[] bytes = resp.getBody().readAllBytes();
            if (bytes.length > 0) {
                String body = new String(bytes, StandardCharsets.UTF_8);
                int msgIdx = body.indexOf("\"message\":\"");
                if (msgIdx != -1) {
                    int endIdx = body.indexOf("\"", msgIdx + 11);
                    if (endIdx != -1) {
                        return body.substring(msgIdx + 11, endIdx);
                    }
                }
                if (body.length() < 256) {
                    return body;
                }
            }
        } catch (Exception ignored) {
        }
        return defaultMsg;
    }
}
