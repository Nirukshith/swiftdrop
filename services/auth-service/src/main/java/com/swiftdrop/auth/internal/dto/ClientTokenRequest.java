package com.swiftdrop.auth.internal.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ClientTokenRequest(
    @JsonProperty("client_id")
    @JsonAlias("clientId")
    @NotBlank(message = "Client ID is required")
    String clientId,

    @JsonProperty("client_secret")
    @JsonAlias("clientSecret")
    @NotBlank(message = "Client secret is required")
    String clientSecret,

    @JsonProperty("grant_type")
    @JsonAlias("grantType")
    String grantType,

    @JsonProperty("scope")
    String scope
) {
    public ClientTokenRequest(String clientId, String clientSecret) {
        this(clientId, clientSecret, "client_credentials", null);
    }
}
