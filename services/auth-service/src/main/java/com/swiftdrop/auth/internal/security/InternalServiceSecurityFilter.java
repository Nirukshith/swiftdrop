package com.swiftdrop.auth.internal.security;

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
 * Enforces authenticated service-to-service communication on internal endpoints (/internal/v1/**).
 * Rejects anonymous callers, ordinary user JWTs, merchant client tokens, and spoofable headers.
 * Applies least-privilege access control based on internal service identity.
 */
@Component
@Order(-100)
public class InternalServiceSecurityFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";

    private final String notificationsServiceToken;
    private final String merchantServiceToken;

    public InternalServiceSecurityFilter(
            @Value("${swiftdrop.security.internal.tokens.notifications-service:swiftdrop-internal-notif-secret-2026}")
            String notificationsServiceToken,
            @Value("${swiftdrop.security.internal.tokens.merchant-service:swiftdrop-internal-merchant-secret-2026}")
            String merchantServiceToken) {
        this.notificationsServiceToken = notificationsServiceToken;
        this.merchantServiceToken = merchantServiceToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/internal/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token == null || token.isBlank()) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Missing internal service token");
            return;
        }

        String caller;
        if (token.equals(notificationsServiceToken)) {
            caller = "notifications-service";
        } else if (token.equals(merchantServiceToken)) {
            caller = "merchant-service";
        } else {
            sendError(response, HttpStatus.UNAUTHORIZED, "Invalid internal service token");
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Least-privilege authorization matrix
        boolean authorized = false;

        if (path.startsWith("/internal/v1/users/") && path.endsWith("/contact") && "GET".equalsIgnoreCase(method)) {
            // User contact lookup: permitted for notifications-service and merchant-service
            authorized = "notifications-service".equals(caller) || "merchant-service".equals(caller);
        } else if (path.equals("/internal/v1/users") && "POST".equalsIgnoreCase(method)) {
            // User creation: permitted ONLY for merchant-service
            authorized = "merchant-service".equals(caller);
        } else if (path.startsWith("/internal/v1/users/") && path.endsWith("/activate") && "POST".equalsIgnoreCase(method)) {
            // User activation: permitted ONLY for merchant-service
            authorized = "merchant-service".equals(caller);
        } else if (path.matches("/internal/v1/merchant-provisioning/[0-9a-fA-F-]{36}(/credentials)?")
                && (("GET".equals(method) || "PUT".equals(method)) && !path.endsWith("/credentials")
                    || "POST".equals(method) && path.endsWith("/credentials"))) {
            authorized = "merchant-service".equals(caller);
        } else if (path.startsWith("/internal/v1/clients") && "POST".equalsIgnoreCase(method)) {
            // Client creation & token exchange: permitted for merchant-service
            authorized = "merchant-service".equals(caller);
        }

        if (!authorized) {
            sendError(response, HttpStatus.FORBIDDEN,
                    "Access denied: service '" + caller + "' is not authorized for " + method + " " + path);
            return;
        }

        // Establish internal service security context
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                caller,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
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
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String candidate = authHeader.substring(7).trim();
            if (candidate.equals(notificationsServiceToken) || candidate.equals(merchantServiceToken)) {
                return candidate;
            }
        }
        return null;
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format("{\"error\":\"%s\",\"message\":\"%s\",\"status\":%d}",
                status.getReasonPhrase(), message, status.value()));
    }
}
