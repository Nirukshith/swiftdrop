package com.swiftdrop.common.security;

import com.swiftdrop.common.Roles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.*;
import java.util.stream.Collectors;

public record CurrentUser(
    String userId,
    String email,
    String role,
    Set<String> roles,
    String merchantId,
    Set<String> authorities
) {

    public static Optional<CurrentUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.of(from(auth));
    }

    public static CurrentUser require() {
        return get().orElseThrow(() -> new AccessDeniedException("No authenticated security context found"));
    }

    public static CurrentUser from(Authentication auth) {
        String userId = auth.getName();
        String email = null;
        String role = null;
        String merchantId = null;
        Set<String> rolesSet = new LinkedHashSet<>();

        Set<String> authorities = auth.getAuthorities() != null
            ? auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet())
            : Set.of();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return from(jwt, authorities);
        } else if (auth.getPrincipal() instanceof Jwt jwt) {
            return from(jwt, authorities);
        }

        // Fallback for non-JWT authentications (e.g. tests)
        for (String a : authorities) {
            if (a.startsWith("ROLE_")) {
                rolesSet.add(a.substring(5));
            }
        }
        if (!rolesSet.isEmpty()) {
            role = rolesSet.iterator().next();
        }

        return new CurrentUser(userId, email, role, Collections.unmodifiableSet(rolesSet), merchantId, authorities);
    }

    public static CurrentUser from(Jwt jwt) {
        SwiftdropJwtGrantedAuthoritiesConverter converter = new SwiftdropJwtGrantedAuthoritiesConverter();
        Collection<GrantedAuthority> granted = converter.convert(jwt);
        Set<String> authorities = granted.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toUnmodifiableSet());
        return from(jwt, authorities);
    }

    private static CurrentUser from(Jwt jwt, Set<String> authorities) {
        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString(JwtClaims.EMAIL);
        String merchantId = jwt.getClaimAsString(JwtClaims.MERCHANT_ID);
        String role = jwt.getClaimAsString(JwtClaims.ROLE);

        Set<String> rolesSet = new LinkedHashSet<>();
        if (role != null && !role.isBlank()) {
            rolesSet.add(normalizeRoleName(role));
        }

        Object rolesObj = jwt.getClaims().get(JwtClaims.ROLES);
        if (rolesObj instanceof Collection<?> coll) {
            for (Object item : coll) {
                if (item instanceof String s && !s.isBlank()) {
                    rolesSet.add(normalizeRoleName(s));
                }
            }
        }

        // Fallback: extract from authorities if role claim was missing
        if (rolesSet.isEmpty()) {
            for (String a : authorities) {
                if (a.startsWith("ROLE_")) {
                    rolesSet.add(a.substring(5));
                }
            }
        }

        if (role == null && !rolesSet.isEmpty()) {
            role = rolesSet.iterator().next();
        } else if (role != null) {
            role = normalizeRoleName(role);
        }

        return new CurrentUser(
            userId,
            email,
            role,
            Collections.unmodifiableSet(rolesSet),
            merchantId,
            authorities
        );
    }

    private static String normalizeRoleName(String r) {
        String trimmed = r.trim().toUpperCase(Locale.ROOT);
        return trimmed.startsWith("ROLE_") ? trimmed.substring(5) : trimmed;
    }

    public boolean hasRole(String targetRole) {
        if (targetRole == null || targetRole.isBlank()) {
            return false;
        }
        String normalized = normalizeRoleName(targetRole);
        return roles.contains(normalized) || authorities.contains("ROLE_" + normalized);
    }

    public boolean isCourier() {
        return hasRole(Roles.COURIER);
    }

    public boolean isCustomer() {
        return hasRole(Roles.CUSTOMER);
    }

    public boolean isMerchant() {
        return hasRole(Roles.MERCHANT);
    }

    public boolean isDispatcher() {
        return hasRole(Roles.DISPATCHER);
    }

    public boolean isAdmin() {
        return hasRole(Roles.ADMIN);
    }
}
