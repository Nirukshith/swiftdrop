package com.swiftdrop.auth.token;

import com.swiftdrop.auth.client.ClientTokenService;
import com.swiftdrop.auth.internal.dto.ClientTokenRequest;
import com.swiftdrop.auth.internal.dto.ClientTokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public OAuth 2.0 client-credentials token endpoint.
 * Routed through the Nginx API gateway at POST /api/v1/auth/token.
 * Aligned with contracts/openapi/auth.yaml and docs/merchant-design.md.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class OAuthTokenController {

    private final ClientTokenService clientTokenService;

    public OAuthTokenController(ClientTokenService clientTokenService) {
        this.clientTokenService = clientTokenService;
    }

    /**
     * JSON body exchange.
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ClientTokenResponse> issueTokenJson(
            @Valid @RequestBody(required = false) ClientTokenRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return ResponseEntity.ok(clientTokenService.exchangeToken(request, authorizationHeader));
    }

    /**
     * Form URL-encoded body exchange (standard OAuth2 RFC 6749 form post).
     */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ClientTokenResponse> issueTokenForm(
            @RequestParam Map<String, String> params,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String clientId = params.getOrDefault("client_id", params.get("clientId"));
        String clientSecret = params.getOrDefault("client_secret", params.get("clientSecret"));
        String grantType = params.getOrDefault("grant_type", params.get("grantType"));
        String scope = params.get("scope");

        ClientTokenRequest request = new ClientTokenRequest(clientId, clientSecret, grantType, scope);
        return ResponseEntity.ok(clientTokenService.exchangeToken(request, authorizationHeader));
    }
}
