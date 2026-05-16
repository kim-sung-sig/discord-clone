package com.example.discord.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionSecretValidationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(ProductionSecretConfiguration.class);

    @Test
    void productionProfileFailsWhenPostgresProfileIsMissing() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=production",
                "discord.auth.access-token-secret=prod-auth-secret-prod-auth-secret",
                "discord.gateway.internal-publisher-token=prod-gateway-token-prod-gateway-token",
                "spring.datasource.url=jdbc:postgresql://postgres:5432/discord",
                "spring.datasource.username=discord_prod",
                "spring.datasource.password=prod-db-password-prod-db-password"
            )
            .run(context -> assertThat(context.getStartupFailure())
                .hasMessageContaining("production profile requires postgres profile"));
    }

    @Test
    void productionProfileFailsWhenDefaultSecretsAreUsed() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=production,postgres",
                "discord.auth.access-token-secret=local-development-auth-secret",
                "discord.gateway.internal-publisher-token=test-harness",
                "spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/discord",
                "spring.datasource.username=dev_user",
                "spring.datasource.password=dev_password"
            )
            .run(context -> assertThat(context.getStartupFailure())
                .hasMessageContaining("production secret/config validation failed")
                .hasMessageContaining("discord.auth.access-token-secret")
                .hasMessageContaining("discord.gateway.internal-publisher-token")
                .hasMessageContaining("spring.datasource.password"));
    }

    @Test
    void productionProfileStartsWhenRequiredSecretsAreExplicit() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=production,postgres",
                "discord.auth.access-token-secret=prod-auth-secret-prod-auth-secret",
                "discord.gateway.internal-publisher-token=prod-gateway-token-prod-gateway-token",
                "spring.datasource.url=jdbc:postgresql://postgres:5432/discord",
                "spring.datasource.username=discord_prod",
                "spring.datasource.password=prod-db-password-prod-db-password"
            )
            .run(context -> assertThat(context.getStartupFailure()).isNull());
    }
}
