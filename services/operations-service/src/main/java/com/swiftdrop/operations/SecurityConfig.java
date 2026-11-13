package com.swiftdrop.operations;

import java.util.Collection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import com.swiftdrop.operations.pricing.InternalPricingServiceFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final InternalPricingServiceFilter internalPricingServiceFilter;

    public SecurityConfig(InternalPricingServiceFilter internalPricingServiceFilter) {
        this.internalPricingServiceFilter = internalPricingServiceFilter;
    }

    @Bean
    public JwtDecoder jwtDecoder(
        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:http://localhost:8081/.well-known/jwks.json}")
        String jwksUri,
        @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:https://auth.swiftdrop.local}")
        String issuer,
        @Value("${swiftdrop.security.oauth2.audience:swiftdrop-api}")
        String audience
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build();
        decoder.setJwtValidator(tokenValidator(issuer, audience));
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        ObjectProvider<JwtDecoder> jwtDecoderProvider,
        ObjectProvider<JwtAuthenticationConverter> jwtConverterProvider
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // InternalPricingServiceFilter runs before AuthorizationFilter; for internal
            // callers it sets a synthetic SCOPE_pricing:read authentication so that the
            // JWT resource-server chain is bypassed for those requests.
            .addFilterBefore(internalPricingServiceFilter, AuthorizationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
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

    static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            new AudienceValidator(audience)
        );
    }

    private static final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

        private final String audience;

        private AudienceValidator(String audience) {
            this.audience = audience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            Collection<String> audiences = token.getAudience();
            if (audiences != null && audiences.contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "The required audience is missing",
                null
            ));
        }
    }
}
