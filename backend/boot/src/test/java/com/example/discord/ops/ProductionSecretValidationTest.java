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
                "spring.datasource.password=prod-db-password-prod-db-password",
                "spring.data.redis.host=redis"
            )
            .run(context -> assertThat(context.getStartupFailure())
                .hasMessageContaining("production profile requires postgres profile"));
    }

    @Test
    void productionProfileFailsWhenRedisProfileIsMissing() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=production,postgres",
                "discord.auth.access-token-secret=prod-auth-secret-prod-auth-secret",
                "discord.gateway.internal-publisher-token=prod-gateway-token-prod-gateway-token",
                "spring.datasource.url=jdbc:postgresql://postgres:5432/discord",
                "spring.datasource.username=discord_prod",
                "spring.datasource.password=prod-db-password-prod-db-password",
                "spring.data.redis.host=redis"
            )
            .run(context -> assertThat(context.getStartupFailure())
                .hasMessageContaining("production profile requires redis profile"));
    }

    @Test
    void productionProfileFailsWhenDefaultSecretsAreUsed() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=production,postgres,redis",
                "discord.auth.access-token-secret=local-development-auth-secret",
                "discord.gateway.internal-publisher-token=test-harness",
                "spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/discord",
                "spring.datasource.username=dev_user",
                "spring.datasource.password=dev_password",
                "spring.data.redis.host=127.0.0.1"
            )
            .run(context -> assertThat(context.getStartupFailure())
                .hasMessageContaining("production secret/config validation failed")
                .hasMessageContaining("discord.auth.access-token-secret")
                .hasMessageContaining("discord.gateway.internal-publisher-token")
                .hasMessageContaining("spring.datasource.password")
                .hasMessageContaining("spring.data.redis.host"));
    }

    @Test
    void productionProfileStartsWhenRequiredSecretsAreExplicit() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=production,postgres,redis",
                "discord.auth.access-token-secret=prod-auth-secret-prod-auth-secret",
                "discord.gateway.internal-publisher-token=prod-gateway-token-prod-gateway-token",
                "spring.datasource.url=jdbc:postgresql://postgres:5432/discord",
                "spring.datasource.username=discord_prod",
                "spring.datasource.password=prod-db-password-prod-db-password",
                "spring.data.redis.host=redis"
            )
            .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void productionMediaLiveKitProfileFailsWhenLiveKitSecretsAreMissing() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=production,postgres,redis,media-livekit",
                "discord.auth.access-token-secret=prod-auth-secret-prod-auth-secret",
                "discord.gateway.internal-publisher-token=prod-gateway-token-prod-gateway-token",
                "spring.datasource.url=jdbc:postgresql://postgres:5432/discord",
                "spring.datasource.username=discord_prod",
                "spring.datasource.password=prod-db-password-prod-db-password",
                "spring.data.redis.host=redis"
            )
            .run(context -> assertThat(context.getStartupFailure())
                .hasMessageContaining("discord.media.livekit.api-key")
                .hasMessageContaining("discord.media.livekit.api-secret")
                .hasMessageContaining("discord.media.livekit.url"));
    }

    @Test
    void productionMediaLiveKitProfileStartsWhenLiveKitSecretsAreExplicit() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=production,postgres,redis,media-livekit",
                "discord.auth.access-token-secret=prod-auth-secret-prod-auth-secret",
                "discord.gateway.internal-publisher-token=prod-gateway-token-prod-gateway-token",
                "spring.datasource.url=jdbc:postgresql://postgres:5432/discord",
                "spring.datasource.username=discord_prod",
                "spring.datasource.password=prod-db-password-prod-db-password",
                "spring.data.redis.host=redis",
                "discord.media.livekit.api-key=lk-prod-key",
                "discord.media.livekit.api-secret=livekit-production-secret-value-32",
                "discord.media.livekit.url=wss://livekit.example.com"
            )
            .run(context -> assertThat(context.getStartupFailure()).isNull());
    }
}
