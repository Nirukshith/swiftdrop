package com.swiftdrop.parcel.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Returns 429 with Retry-After and the shared ErrorResponse body when a client IP is over the limit. */
final class TrackingRateLimitFilter extends OncePerRequestFilter {

    static final String CODE = "TOO_MANY_REQUESTS";
    static final String MESSAGE = "Too many tracking requests, please retry later";

    private final ClientIpResolver clientIpResolver;
    private final TrackingRateLimiter limiter;
    private final ObjectMapper objectMapper;

    TrackingRateLimitFilter(ClientIpResolver clientIpResolver, TrackingRateLimiter limiter, ObjectMapper objectMapper) {
        this.clientIpResolver = clientIpResolver;
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TrackingRateLimiter.Decision decision = limiter.tryAcquire(clientIpResolver.resolve(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), TrackingErrors.body(CODE, MESSAGE, request));
    }
}
