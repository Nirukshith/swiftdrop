package com.swiftdrop.auth.internal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClientTokenResponse(
    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("token_type")
    String tokenType,

    @JsonProperty("expires_in")
    long expiresIn,

    @JsonProperty("scope")
    String scope
) {
    public ClientTokenResponse(String accessToken, String tokenType, long expiresIn) {
        this(accessToken, tokenType, expiresIn, null);
    }
}
