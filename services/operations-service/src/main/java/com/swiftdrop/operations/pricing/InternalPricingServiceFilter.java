package com.swiftdrop.operations.pricing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Guards {@code /internal/v1/pricing/**} endpoints in operations-service.
 *
 * <p>Accepts requests from internal SwiftDrop services that present the shared
 * {@code X-Internal-Service-Token} header. Authorized callers receive
 * {@code SCOPE_pricing:read} so that {@link QuoteController}'s
 * {@code @PreAuthorize("hasAuthority('SCOPE_pricing:read')")} check passes
 * without requiring a user-facing JWT.
 *
 * <p>Runs at order -100 (before Spring Security's AuthorizationFilter) so that
 * the security context is established before method-level authorization is applied.
 *
 * <p>Responsibility: M10 (auth / security configuration).
 * This filter was introduced to resolve the Pricing API 401 blocker (Issue #220).
 */
@Component
@Order(-100)
public class InternalPricingServiceFilter extends OncePerRequestFilter {

    /** Shared header name, consistent with auth-service's InternalServiceSecurityFilter. */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";

    private final String pricingServiceToken;

    public InternalPricingServiceFilter(
            @Value("${swiftdrop.security.internal.tokens.pricing-service:swiftdrop-internal-pricing-secret-2026}")
            String pricingServiceToken) {
        this.pricingServiceToken = pricingServiceToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only intercept the internal pricing endpoint
        return !request.getRequestURI().startsWith("/internal/v1/pricing");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token == null || !token.equals(pricingServiceToken)) {
            // No matching internal token — let the JWT resource-server chain handle it
            // (a human dispatcher with a valid JWT will still pass)
            filterChain.doFilter(request, response);
            return;
        }

        // Establish a synthetic security context granting pricing:read scope
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "internal-pricing-caller",
                null,
                List.of(
                        new SimpleGrantedAuthority("SCOPE_pricing:read"),
                        new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return null;
    }
}
