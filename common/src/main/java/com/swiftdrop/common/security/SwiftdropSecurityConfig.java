package com.swiftdrop.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class})
public class SwiftdropSecurityConfig {

    @Bean
    @ConditionalOnMissingBean
    public SwiftdropJwtGrantedAuthoritiesConverter swiftdropJwtGrantedAuthoritiesConverter() {
        return new SwiftdropJwtGrantedAuthoritiesConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter(SwiftdropJwtGrantedAuthoritiesConverter authoritiesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(name = {"spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "swiftdrop.security.jwks-url"})
    public JwtDecoder jwtDecoder(
        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:${swiftdrop.security.jwks-url:}}") String jwksUrl
    ) {
        if (jwksUrl == null || jwksUrl.isBlank()) {
            throw new IllegalStateException("Neither spring.security.oauth2.resourceserver.jwt.jwk-set-uri nor swiftdrop.security.jwks-url is configured");
        }
        return NimbusJwtDecoder.withJwkSetUri(jwksUrl).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public OwnershipCheck ownershipCheck() {
        return new OwnershipCheck();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    @ConditionalOnBean(HttpSecurity.class)
    public SecurityFilterChain defaultSecurityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health/**",
                    "/actuator/info",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            );

        return http.build();
    }
}
