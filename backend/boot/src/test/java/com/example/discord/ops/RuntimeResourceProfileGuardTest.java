package com.example.discord.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class RuntimeResourceProfileGuardTest {
    @Test
    void productionProfileRequiresPostgresProfile() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.profiles.active", "production");
        environment.setActiveProfiles("production");

        assertThatThrownBy(() -> RuntimeResourceProfileGuard.validateProfiles(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("production-like runtime profiles require postgres");
    }

    @Test
    void environmentPostProcessorFailsBeforeBeanCreation() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.profiles.active", "production");
        environment.setActiveProfiles("production");
        RuntimeResourceProfileEnvironmentPostProcessor postProcessor = new RuntimeResourceProfileEnvironmentPostProcessor();

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("production-like runtime profiles require postgres");
    }

    @Test
    void environmentPostProcessorIsRegisteredWithSpringBoot() throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream("META-INF/spring.factories")) {
            assertThat(input).isNotNull();
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(content)
                .contains("org.springframework.boot.env.EnvironmentPostProcessor")
                .contains(RuntimeResourceProfileEnvironmentPostProcessor.class.getName());
        }
    }

    @Test
    void adminCliProfileRequiresPostgresProfile() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.profiles.active", "admin-cli");
        environment.setActiveProfiles("admin-cli");

        assertThatThrownBy(() -> RuntimeResourceProfileGuard.validateProfiles(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("production-like runtime profiles require postgres");
    }

    @Test
    void productionProfileAllowsPostgresProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production", "postgres");

        assertThatCode(() -> RuntimeResourceProfileGuard.validateProfiles(environment))
            .doesNotThrowAnyException();
    }

    @Test
    void testProfileAllowsInMemoryStores() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertThatCode(() -> RuntimeResourceProfileGuard.validateProfiles(environment))
            .doesNotThrowAnyException();
    }
}
