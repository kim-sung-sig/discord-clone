package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_CENTRAL_REDIS_GATEWAY_SMOKE", matches = "true")
class CentralRedisGatewaySessionRegistrySmokeTest {
    @Test
    void sessionRegistryRefreshesTtlAndPrunesExpiredIndexEntriesThroughCentralRedis() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            String registryKey = "gateway:sessions:smoke:" + UUID.randomUUID();
            RedisGatewaySessionRegistry registry = new RedisGatewaySessionRegistry(
                redis,
                new ObjectMapper(),
                registryKey,
                60L
            );
            GatewaySession session = new GatewaySession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Set.of(UUID.randomUUID()),
                Instant.parse("2026-05-21T00:00:00Z"),
                false,
                9L
            );

            registry.save(session);

            String sessionKey = registryKey + ":" + session.id();
            assertThat(redis.getExpire(sessionKey)).isPositive();
            assertThat(registry.find(session.id())).contains(session);
            redis.delete(sessionKey);

            assertThat(registry.sessions()).isEmpty();
            assertThat(redis.opsForSet().isMember(registryKey, session.id().toString())).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    private static LettuceConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
            envOrDefault("SPRING_DATA_REDIS_HOST", "127.0.0.1"),
            Integer.parseInt(envOrDefault("SPRING_DATA_REDIS_PORT", "16379"))
        );
        configuration.setPassword(RedisPassword.of(envOrDefault("SPRING_DATA_REDIS_PASSWORD", "dev_password")));
        return new LettuceConnectionFactory(configuration);
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
