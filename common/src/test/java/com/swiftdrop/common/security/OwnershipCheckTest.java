package com.swiftdrop.common.security;

import com.swiftdrop.common.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class OwnershipCheckTest {

    private final OwnershipCheck ownershipCheck = new OwnershipCheck();
    private final SwiftdropJwtGrantedAuthoritiesConverter authoritiesConverter = new SwiftdropJwtGrantedAuthoritiesConverter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String userId, String role, String merchantId) {
        Jwt.Builder builder = Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .subject(userId)
            .claim(JwtClaims.EMAIL, userId + "@swiftdrop.local")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600));

        if (role != null) {
            builder.claim(JwtClaims.ROLE, role);
        }
        if (merchantId != null) {
            builder.claim(JwtClaims.MERCHANT_ID, merchantId);
        }

        Jwt jwt = builder.build();
        var authorities = authoritiesConverter.convert(jwt);
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, authorities, userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("isOwner and checkOwner should succeed for matching user and fail for mismatched")
    void shouldValidateResourceOwner() {
        authenticateWith("user-100", Roles.CUSTOMER, null);

        assertTrue(ownershipCheck.isOwner("user-100"));
        assertDoesNotThrow(() -> ownershipCheck.checkOwner("user-100"));

        assertFalse(ownershipCheck.isOwner("user-200"));
        assertThrows(AccessDeniedException.class, () -> ownershipCheck.checkOwner("user-200"));
    }

    @Test
    @DisplayName("isCourierOwner and checkCourierOwner should verify courier role and assigned ID")
    void shouldValidateCourierOwnership() {
        authenticateWith("courier-42", Roles.COURIER, null);

        assertTrue(ownershipCheck.isCourierOwner("courier-42"));
        assertDoesNotThrow(() -> ownershipCheck.checkCourierOwner("courier-42"));

        assertFalse(ownershipCheck.isCourierOwner("courier-99"));
        assertThrows(AccessDeniedException.class, () -> ownershipCheck.checkCourierOwner("courier-99"));

        // If user is not COURIER role, checkCourierOwner must fail even if ID matches
        authenticateWith("courier-42", Roles.CUSTOMER, null);
        assertFalse(ownershipCheck.isCourierOwner("courier-42"));
        assertThrows(AccessDeniedException.class, () -> ownershipCheck.checkCourierOwner("courier-42"));
    }

    @Test
    @DisplayName("isMerchantOwner should verify merchantId claim and allow Admin override")
    void shouldValidateMerchantOwnership() {
        authenticateWith("user-m1", Roles.MERCHANT, "merchant-shop-abc");

        assertTrue(ownershipCheck.isMerchantOwner("merchant-shop-abc"));
        assertDoesNotThrow(() -> ownershipCheck.checkMerchantOwner("merchant-shop-abc"));

        assertFalse(ownershipCheck.isMerchantOwner("other-merchant-shop"));
        assertThrows(AccessDeniedException.class, () -> ownershipCheck.checkMerchantOwner("other-merchant-shop"));

        // Admin override test
        authenticateWith("admin-1", Roles.ADMIN, null);
        assertTrue(ownershipCheck.isMerchantOwner("any-merchant-shop"));
        assertDoesNotThrow(() -> ownershipCheck.checkMerchantOwner("any-merchant-shop"));
    }

    @Test
    @DisplayName("isOwnerOrAdmin should allow owner, admin, or dispatcher")
    void shouldAllowOwnerOrAdmin() {
        // As resource owner
        authenticateWith("user-50", Roles.CUSTOMER, null);
        assertTrue(ownershipCheck.isOwnerOrAdmin("user-50"));
        assertDoesNotThrow(() -> ownershipCheck.checkOwnerOrAdmin("user-50"));

        // As Admin for different owner
        authenticateWith("admin-root", Roles.ADMIN, null);
        assertTrue(ownershipCheck.isOwnerOrAdmin("user-50"));
        assertDoesNotThrow(() -> ownershipCheck.checkOwnerOrAdmin("user-50"));

        // As Dispatcher for different owner
        authenticateWith("dispatcher-ops", Roles.DISPATCHER, null);
        assertTrue(ownershipCheck.isOwnerOrAdmin("user-50"));
        assertDoesNotThrow(() -> ownershipCheck.checkOwnerOrAdmin("user-50"));

        // As unauthorized third-party user
        authenticateWith("stranger-user", Roles.CUSTOMER, null);
        assertFalse(ownershipCheck.isOwnerOrAdmin("user-50"));
        assertThrows(AccessDeniedException.class, () -> ownershipCheck.checkOwnerOrAdmin("user-50"));
    }

    @Test
    @DisplayName("Unauthenticated context should fail ownership checks")
    void shouldFailWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertFalse(ownershipCheck.isOwner("user-100"));
        assertThrows(AccessDeniedException.class, () -> ownershipCheck.checkOwner("user-100"));
        assertFalse(ownershipCheck.isCourierOwner("courier-1"));
        assertFalse(ownershipCheck.isMerchantOwner("merchant-1"));
        assertFalse(ownershipCheck.isOwnerOrAdmin("user-100"));
    }
}
