package com.swiftdrop.common.security;

import com.swiftdrop.common.Roles;
import com.swiftdrop.common.security.dev.DevJwtTokenMinter;
import com.swiftdrop.common.security.dev.DevSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevProfileOffByDefaultTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            SwiftdropSecurityConfig.class,
            DevSecurityConfig.class,
            DevJwtTokenMinter.class
        ));

    @Test
    @DisplayName("DevJwtTokenMinter and DevSecurityConfig must NOT be loaded when dev profile is not active")
    void devBeansMustBeOffByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(DevJwtTokenMinter.class);
            assertThat(context).doesNotHaveBean(DevSecurityConfig.class);
        });
    }

    @Test
    @DisplayName("DevJwtTokenMinter must NOT be loaded in prod profile")
    void devBeansMustBeOffInProduction() {
        contextRunner
            .withPropertyValues("spring.profiles.active=prod")
            .run(context -> {
                assertThat(context).doesNotHaveBean(DevJwtTokenMinter.class);
                assertThat(context).doesNotHaveBean(DevSecurityConfig.class);
            });
    }

    @Test
    @DisplayName("Direct instantiation outside dev profile must throw IllegalStateException")
    void directInstantiationMustFailOutsideDevProfile() {
        MockEnvironment nonDevEnv = new MockEnvironment();
        nonDevEnv.setActiveProfiles("production");

        assertThrows(IllegalStateException.class, () -> new DevJwtTokenMinter(nonDevEnv));
    }

    @Test
    @DisplayName("DevJwtTokenMinter must be loaded and functional when dev profile is active")
    void devBeansMustBeActiveInDevProfile() {
        contextRunner
            .withPropertyValues("spring.profiles.active=dev")
            .run(context -> {
                assertThat(context).hasSingleBean(DevJwtTokenMinter.class);
                assertThat(context).hasSingleBean(DevSecurityConfig.class);

                DevJwtTokenMinter minter = context.getBean(DevJwtTokenMinter.class);
                JwtDecoder decoder = context.getBean(JwtDecoder.class);

                String token = minter.mintCourierToken("courier-dev-1");
                assertThat(token).isNotBlank();

                Jwt jwt = decoder.decode(token);
                assertThat(jwt.getSubject()).isEqualTo("courier-dev-1");
                assertThat(jwt.getClaimAsString(JwtClaims.ROLE)).isEqualTo(Roles.COURIER);
            });
    }
}
