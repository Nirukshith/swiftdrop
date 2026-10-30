package com.swiftdrop.integration.merchant;

import static com.swiftdrop.integration.merchant.AuthProvisioningClient.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpAuthProvisioningClient implements AuthProvisioningClient {

    private final RestClient restClient;

    public HttpAuthProvisioningClient(@Value("${services.auth.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public AuthUserResponse createMerchantUser(AuthCreateUserRequest request) {
        return restClient.post().uri("/internal/v1/users").body(request).retrieve().body(AuthUserResponse.class);
    }

    @Override
    public AuthUserResponse activateUser(String userId) {
        return restClient.post().uri("/internal/v1/users/{id}/activate", userId)
                .retrieve().body(AuthUserResponse.class);
    }

    @Override
    public AuthClientCredentialsResponse createClient(AuthCreateClientRequest request) {
        return restClient.post().uri("/internal/v1/clients").body(request)
                .retrieve().body(AuthClientCredentialsResponse.class);
    }
}
