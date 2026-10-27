package com.swiftdrop.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Base security configuration for auth-service.
 *
 * <p>Public (unauthenticated) paths:
 * <ul>
 *   <li>{@code /.well-known/jwks.json}      – JWKS public key set (consumed by other services)</li>
 *   <li>{@code /api/v1/auth/register}        – Self-service customer registration</li>
 *   <li>{@code /api/v1/auth/login}           – Credential login</li>
 *   <li>{@code /api/v1/auth/refresh}         – Refresh token rotation</li>
 *   <li>{@code /api/v1/auth/oauth2/**}       – Google OAuth flow (initiate + callback)</li>
 *   <li>{@code /api/v1/auth/password/**}     – Password reset (M3)</li>
 *   <li>{@code /internal/v1/**}              – Service-to-service internal APIs (blocked at Gateway)</li>
 *   <li>{@code /actuator/health/**}          – Health probe</li>
 * </ul>
 *
 * <p>All other paths require a valid bearer token.
 * LoginGuard (M7) and AuditService (M8) are wired in as in-process interfaces.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Public endpoints – no bearer token required
                .requestMatchers("/.well-known/jwks.json").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/oauth2/**",
                    "/api/v1/auth/password/**",
                    "/internal/v1/**"
                ).permitAll()
                // Everything else (admin, etc.) requires authentication
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
