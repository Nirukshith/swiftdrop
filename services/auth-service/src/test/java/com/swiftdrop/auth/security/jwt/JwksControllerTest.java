package com.swiftdrop.auth.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwksControllerTest {

    private JwksController jwksController;

    @BeforeEach
    void setUp() {
        JwtKeyProvider keyProvider = new JwtKeyProvider("", "", JwtKeyProvider.DEV_KEY_ID);
        jwksController = new JwksController(keyProvider);
    }

    @Test
    @DisplayName("GET /.well-known/jwks.json returns RFC 7517 public JWK set without private keys")
    void jwks_returnsPublicJwkSet() {
        Map<String, Object> response = jwksController.jwks();

        assertThat(response).containsKey("keys");
        Object keysObj = response.get("keys");
        assertThat(keysObj).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) keysObj;
        assertThat(keys).hasSize(1);

        Map<String, Object> key = keys.get(0);
        assertThat(key.get("kty")).isEqualTo("RSA");
        assertThat(key.get("kid")).isEqualTo(JwtKeyProvider.DEV_KEY_ID);
        assertThat(key.get("n")).isNotNull();
        assertThat(key.get("e")).isNotNull();

        // Ensure no private key material is exposed
        assertThat(key).doesNotContainKey("d");
        assertThat(key).doesNotContainKey("p");
        assertThat(key).doesNotContainKey("q");
        assertThat(key).doesNotContainKey("dp");
        assertThat(key).doesNotContainKey("dq");
        assertThat(key).doesNotContainKey("qi");
    }
}

