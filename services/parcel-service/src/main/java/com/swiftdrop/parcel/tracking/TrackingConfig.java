package com.swiftdrop.parcel.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Wiring for the public tracking endpoints: their own security chain (no login, GET only)
 * and the per-IP rate limit filter. Kept here so the parcel package's SecurityConfig is untouched.
 */
@Configuration
@EnableConfigurationProperties(TrackingRateLimitProperties.class)
class TrackingConfig {

    static final String PUBLIC_TRACKING = "/api/v1/tracking/**";

    /** Runs before the parcel package's chain, and only for /api/v1/tracking/**. */
    @Bean
    @Order(1)
    SecurityFilterChain publicTrackingSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(PUBLIC_TRACKING)
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_TRACKING).permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "swiftdrop.tracking.rate-limit", name = "enabled", matchIfMissing = true)
    FilterRegistrationBean<TrackingRateLimitFilter> trackingRateLimitFilter(
            TrackingRateLimitProperties properties, ObjectMapper objectMapper) {
        TrackingRateLimiter limiter = new TrackingRateLimiter(
                properties.requestsPerMinute(), properties.burst(), properties.maxTrackedClients(), System::nanoTime);
        TrackingRateLimitFilter filter = new TrackingRateLimitFilter(
                new ClientIpResolver(properties.trustedProxies()), limiter, objectMapper);

        FilterRegistrationBean<TrackingRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/tracking/*");
        // Before Spring Security, so floods are rejected as early as possible.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
