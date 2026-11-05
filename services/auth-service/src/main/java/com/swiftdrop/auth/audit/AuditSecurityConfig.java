package com.swiftdrop.auth.audit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration restricting /api/v1/admin/audit strictly to ROLE_ADMIN.
 * Aligned with ADR 001.
 * Owned by M8.
 */
@Configuration
public class AuditSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain auditSecurityFilterChain(
        HttpSecurity http,
        ObjectProvider<JwtDecoder> jwtDecoderProvider,
        ObjectProvider<JwtAuthenticationConverter> jwtConverterProvider
    ) throws Exception {
        http
            .securityMatcher("/api/v1/admin/audit/**", "/api/v1/admin/audit")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/admin/audit/**", "/api/v1/admin/audit").hasRole("ADMIN")
                .anyRequest().authenticated()
            );

        JwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();
        if (jwtDecoder != null) {
            JwtAuthenticationConverter converter = jwtConverterProvider.getIfAvailable();
            http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {
                    jwt.decoder(jwtDecoder);
                    if (converter != null) {
                        jwt.jwtAuthenticationConverter(converter);
                    }
                })
            );
        }

        return http.build();
    }
}
