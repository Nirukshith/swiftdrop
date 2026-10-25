package com.swiftdrop.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

public class SwiftdropJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String DEFAULT_ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        // 1. Map single "role" claim (e.g. "COURIER" -> "ROLE_COURIER")
        Object roleClaim = jwt.getClaims().get(JwtClaims.ROLE);
        if (roleClaim instanceof String roleStr && !roleStr.isBlank()) {
            authorities.add(toRoleAuthority(roleStr.trim()));
        }

        // 2. Map multiple "roles" claim if present
        Object rolesClaim = jwt.getClaims().get(JwtClaims.ROLES);
        if (rolesClaim instanceof Collection<?> rolesColl) {
            for (Object r : rolesColl) {
                if (r instanceof String roleStr && !roleStr.isBlank()) {
                    authorities.add(toRoleAuthority(roleStr.trim()));
                }
            }
        }

        // 3. Fallback / standard "scope" or "scp"
        extractScopes(jwt, JwtClaims.SCOPE, authorities);
        extractScopes(jwt, JwtClaims.SCP, authorities);

        return Collections.unmodifiableSet(authorities);
    }

    private GrantedAuthority toRoleAuthority(String role) {
        if (role.startsWith(DEFAULT_ROLE_PREFIX)) {
            return new SimpleGrantedAuthority(role.toUpperCase(Locale.ROOT));
        }
        return new SimpleGrantedAuthority(DEFAULT_ROLE_PREFIX + role.toUpperCase(Locale.ROOT));
    }

    private void extractScopes(Jwt jwt, String claimName, Set<GrantedAuthority> authorities) {
        Object claim = jwt.getClaims().get(claimName);
        if (claim instanceof String scopeStr && !scopeStr.isBlank()) {
            for (String scope : scopeStr.split("\\s+")) {
                if (!scope.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                }
            }
        } else if (claim instanceof Collection<?> scopeColl) {
            for (Object s : scopeColl) {
                if (s instanceof String scope && !scope.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                }
            }
        }
    }
}
