package com.swiftdrop.common.security;

import com.swiftdrop.common.Roles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TokenClaimsMappingTest {

    private final SwiftdropJwtGrantedAuthoritiesConverter converter = new SwiftdropJwtGrantedAuthoritiesConverter();

    @Test
    @DisplayName("Should map single role claim to GrantedAuthority with ROLE_ prefix")
    void shouldMapSingleRoleClaim() {
        Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
            .header("alg", "none")
            .subject("courier-uuid-1")
            .claim(JwtClaims.ROLE, Roles.COURIER)
            .claim(JwtClaims.EMAIL, "courier@swiftdrop.local")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertNotNull(authorities);
        Set<String> authorityStrings = authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        assertTrue(authorityStrings.contains("ROLE_COURIER"));
        assertEquals(1, authorityStrings.size());

        CurrentUser user = CurrentUser.from(jwt);
        assertEquals("courier-uuid-1", user.userId());
        assertEquals("courier@swiftdrop.local", user.email());
        assertEquals("COURIER", user.role());
        assertTrue(user.isCourier());
        assertFalse(user.isAdmin());
        assertNull(user.merchantId());
    }

    @Test
    @DisplayName("Should map multiple roles list and merchantId claim")
    void shouldMapMultipleRolesAndMerchantId() {
        Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
            .header("alg", "none")
            .subject("merchant-uuid-99")
            .claim(JwtClaims.ROLES, List.of("MERCHANT", "CUSTOMER"))
            .claim(JwtClaims.EMAIL, "store@merchant.com")
            .claim(JwtClaims.MERCHANT_ID, "m-shop-1234")
            .claim(JwtClaims.SCOPE, "read write")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        Set<String> authorityStrings = authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        assertTrue(authorityStrings.contains("ROLE_MERCHANT"));
        assertTrue(authorityStrings.contains("ROLE_CUSTOMER"));
        assertTrue(authorityStrings.contains("SCOPE_read"));
        assertTrue(authorityStrings.contains("SCOPE_write"));

        CurrentUser user = CurrentUser.from(jwt);
        assertEquals("merchant-uuid-99", user.userId());
        assertEquals("store@merchant.com", user.email());
        assertEquals("m-shop-1234", user.merchantId());
        assertTrue(user.isMerchant());
        assertTrue(user.isCustomer());
        assertFalse(user.isAdmin());
    }

    @Test
    @DisplayName("Should preserve existing ROLE_ prefix without duplication")
    void shouldPreserveExistingRolePrefix() {
        Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
            .header("alg", "none")
            .subject("admin-uuid-0")
            .claim(JwtClaims.ROLE, "ROLE_ADMIN")
            .claim(JwtClaims.EMAIL, "admin@swiftdrop.local")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        Set<String> authorityStrings = authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        assertTrue(authorityStrings.contains("ROLE_ADMIN"));
        assertFalse(authorityStrings.contains("ROLE_ROLE_ADMIN"));

        CurrentUser user = CurrentUser.from(jwt);
        assertTrue(user.isAdmin());
        assertEquals("ADMIN", user.role());
    }
}
