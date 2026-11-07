package com.swiftdrop.auth.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

@Component
public class DefaultGoogleOAuthClient implements GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultGoogleOAuthClient.class);
    private static final List<String> VALID_ISSUERS = List.of(
            "accounts.google.com",
            "https://accounts.google.com"
    );

    private final GoogleOAuthProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DefaultGoogleOAuthClient(GoogleOAuthProperties properties,
                                   ObjectMapper objectMapper) {
        this(properties, RestClient.builder().build(), objectMapper);
    }

    public DefaultGoogleOAuthClient(GoogleOAuthProperties properties,
                                   RestClient restClient,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public GoogleUserInfo exchangeCodeAndGetUserInfo(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("redirect_uri", properties.getRedirectUri());
        body.add("grant_type", "authorization_code");

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Google token exchange request failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google authorization code exchange failed", e);
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty response from Google token endpoint");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String idToken = root.has("id_token") ? root.get("id_token").asText() : null;

            if (idToken == null || idToken.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing id_token in Google response");
            }

            SignedJWT signedJWT = SignedJWT.parse(idToken);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Validate issuer
            String issuer = claims.getIssuer();
            if (issuer == null || !VALID_ISSUERS.contains(issuer)) {
                log.error("Invalid Google ID token issuer: {}", issuer);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Google ID token issuer");
            }

            // Validate audience
            List<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(properties.getClientId())) {
                log.error("Google ID token audience mismatch. Expected: {}, got: {}", properties.getClientId(), audience);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google ID token audience mismatch");
            }

            // Validate expiration
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                log.error("Google ID token is expired: {}", expiration);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google ID token is expired");
            }

            String sub = claims.getSubject();
            String email = claims.getStringClaim("email");
            Boolean emailVerified = claims.getBooleanClaim("email_verified");
            String name = claims.getStringClaim("name");
            String picture = claims.getStringClaim("picture");

            if (sub == null || sub.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google ID token missing subject");
            }
            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google ID token missing email claim");
            }

            return new GoogleUserInfo(sub, email, name, picture, Boolean.TRUE.equals(emailVerified));
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Failed to parse and verify Google ID token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse Google ID token", e);
        }
    }
}

