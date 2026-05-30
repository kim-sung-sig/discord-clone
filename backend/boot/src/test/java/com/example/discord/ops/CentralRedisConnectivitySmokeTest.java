package com.example.discord.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_CENTRAL_REDIS_SMOKE", matches = "true")
class CentralRedisConnectivitySmokeTest {
    @Test
    void backendCanConnectToCentralRedisEndpoint() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
            envOrDefault("SPRING_DATA_REDIS_HOST", "127.0.0.1"),
            Integer.parseInt(envOrDefault("SPRING_DATA_REDIS_PORT", "16379"))
        );
        configuration.setPassword(RedisPassword.of(envOrDefault("SPRING_DATA_REDIS_PASSWORD", "dev_password")));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            redis.opsForValue().set("discord:central-redis-smoke:backend", "PONG");

            assertThat(redis.opsForValue().get("discord:central-redis-smoke:backend")).isEqualTo("PONG");
        } finally {
            connectionFactory.destroy();
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
