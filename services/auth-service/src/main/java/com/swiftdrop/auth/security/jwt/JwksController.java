package com.swiftdrop.auth.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Serves the public RSA JWK Set at the standard OIDC discovery endpoint.
 *
 * <p>All downstream services (parcel-service, operations-service, integration-service)
 * and the Nginx gateway point their {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}
 * here to verify RS256 bearer tokens without calling auth-service on every request.
 *
 * <p>Endpoint: {@code GET /.well-known/jwks.json}
 * This path is NOT prefixed with {@code /api/v1} and is NOT blocked by the Nginx gateway.
 *
 * <p>Owner: M10 (com.swiftdrop.auth.security.jwt).
 */
@RestController
public class JwksController {

    private final JwtKeyProvider keyProvider;

    public JwksController(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /**
     * Returns the public RSA key set in RFC 7517 JWK format.
     *
     * <p>Only the public key component is exposed; the private key never leaves the service.
     *
     * @return JSON object containing a {@code keys} array with the public RSA key
     */
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        // Strip the private key – expose only the public portion
        RSAKey publicKeyOnly = keyProvider.getRsaKey().toPublicJWK();
        JWKSet jwkSet = new JWKSet(publicKeyOnly);
        return jwkSet.toJSONObject();
    }
}
