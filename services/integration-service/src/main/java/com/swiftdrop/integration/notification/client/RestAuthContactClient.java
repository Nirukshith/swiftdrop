package com.swiftdrop.integration.notification.client;

import com.swiftdrop.integration.notification.dto.UserContactDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class RestAuthContactClient implements AuthContactClient {

    private static final Logger log = LoggerFactory.getLogger(RestAuthContactClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 200;

    private final RestClient restClient;
    private final boolean fallbackEnabled;

    @Autowired
    public RestAuthContactClient(
            @Value("${swiftdrop.auth.base-url:http://localhost:8081}") String authBaseUrl,
            @Value("${swiftdrop.auth.contact-fallback-enabled:true}") boolean fallbackEnabled) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .baseUrl(authBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.fallbackEnabled = fallbackEnabled;
    }

    // Constructor for testing / customization
    public RestAuthContactClient(RestClient restClient, boolean fallbackEnabled) {
        this.restClient = restClient;
        this.fallbackEnabled = fallbackEnabled;
    }

    @Override
    public Optional<UserContactDto> getContact(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }

        int attempts = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            try {
                UserContactDto contact = restClient.get()
                        .uri("/internal/v1/users/{id}/contact", userId)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                            if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                                throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "User contact not found");
                            }
                            throw new HttpClientErrorException(response.getStatusCode(), response.getStatusText());
                        })
                        .body(UserContactDto.class);

                return Optional.ofNullable(contact);

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    log.warn("Auth-service returned 404 for user {}: {}", userId, e.getMessage());
                    if (fallbackEnabled) {
                        return Optional.of(createFallbackContact(userId));
                    }
                    return Optional.empty();
                }
                log.error("Client error retrieving contact for user {}: {}", userId, e.getMessage());
                if (fallbackEnabled) {
                    return Optional.of(createFallbackContact(userId));
                }
                throw new ContactResolutionException("Client error resolving contact for user " + userId, e);
            } catch (ResourceAccessException | HttpServerErrorException e) {
                log.warn("Attempt {}/{} failed calling auth-service contact endpoint for {}: {}",
                        attempts, MAX_ATTEMPTS, userId, e.getMessage());

                if (attempts < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ContactResolutionException("Interrupted while waiting to retry contact lookup", ie);
                    }
                    backoff *= 2;
                } else {
                    if (fallbackEnabled) {
                        log.warn("Max retries reached for contact lookup of user {}. Using fallback contact.", userId);
                        return Optional.of(createFallbackContact(userId));
                    }
                    throw new ContactResolutionException("Failed to resolve contact for user " + userId + " after " + MAX_ATTEMPTS + " attempts", e);
                }
            } catch (Exception e) {
                log.error("Unexpected error retrieving contact for user {}: {}", userId, e.getMessage());
                if (fallbackEnabled) {
                    return Optional.of(createFallbackContact(userId));
                }
                throw new ContactResolutionException("Unexpected failure resolving contact for user " + userId, e);
            }
        }

        return fallbackEnabled ? Optional.of(createFallbackContact(userId)) : Optional.empty();
    }

    private UserContactDto createFallbackContact(String userId) {
        return new UserContactDto(
                userId,
                "Valued Customer",
                "customer-" + userId + "@customer.swiftdrop.internal",
                null,
                "CUSTOMER"
        );
    }
}
