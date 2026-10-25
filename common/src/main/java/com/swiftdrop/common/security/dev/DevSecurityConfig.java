package com.swiftdrop.common.security.dev;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Dev-only security configuration providing a fallback JwtDecoder using the DevJwtTokenMinter public key.
 * Only loaded when the 'dev' profile is active.
 */
@Configuration
@Profile("dev")
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class})
public class DevSecurityConfig {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder devJwtDecoder(DevJwtTokenMinter minter) {
        return NimbusJwtDecoder.withPublicKey(minter.getPublicKey()).build();
    }
}
