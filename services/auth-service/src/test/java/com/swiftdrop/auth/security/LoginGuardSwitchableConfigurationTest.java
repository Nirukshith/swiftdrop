package com.swiftdrop.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LoginGuardSwitchableConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NoOpLoginGuard.class, SlidingWindowLoginGuard.class);

    @Test
    @DisplayName("Default configuration loads NoOpLoginGuard when lockout is disabled or absent")
    void defaultConfiguration_loadsNoOpLoginGuard() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LoginGuard.class);
            assertThat(context.getBean(LoginGuard.class)).isInstanceOf(NoOpLoginGuard.class);
        });
    }

    @Test
    @DisplayName("Configuration with swiftdrop.security.lockout.enabled=true loads SlidingWindowLoginGuard")
    void enabledConfiguration_loadsSlidingWindowLoginGuard() {
        contextRunner
                .withPropertyValues("swiftdrop.security.lockout.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(LoginGuard.class);
                    assertThat(context.getBean(LoginGuard.class)).isInstanceOf(SlidingWindowLoginGuard.class);
                });
    }
}
