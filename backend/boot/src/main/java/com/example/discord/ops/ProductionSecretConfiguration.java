package com.example.discord.ops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("production")
class ProductionSecretConfiguration {
    private static final int MIN_SECRET_LENGTH = 32;

    @Bean
    SmartInitializingSingleton productionSecretValidator(Environment environment) {
        return () -> validate(environment);
    }

    private static void validate(Environment environment) {
        List<String> failures = new ArrayList<>();
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (!activeProfiles.contains("postgres")) {
            failures.add("production profile requires postgres profile");
        }
        if (!activeProfiles.contains("redis")) {
            failures.add("production profile requires redis profile");
        }

        requireSecret(
            environment,
            failures,
            "discord.auth.access-token-secret",
            List.of("local-development-auth-secret", "test-secret-with-enough-length")
        );
        requireSecret(
            environment,
            failures,
            "discord.gateway.internal-publisher-token",
            List.of("test-harness")
        );
        requireSecret(
            environment,
            failures,
            "spring.datasource.password",
            List.of("dev_password")
        );
        if (activeProfiles.contains("media-livekit")) {
            requireNonDefault(environment, failures, "discord.media.livekit.api-key", List.of("test", "local", "lk-test-key"));
            requireSecret(
                environment,
                failures,
                "discord.media.livekit.api-secret",
                List.of("test", "local", "livekit-test-secret", "livekit-local-secret")
            );
            requireNonDefault(
                environment,
                failures,
                "discord.media.livekit.url",
                List.of("http://localhost:7880", "ws://localhost:7880", "wss://localhost:7880")
            );
        }
        requireNonDefault(environment, failures, "spring.datasource.username", List.of("dev_user"));
        requireNonDefault(environment, failures, "spring.datasource.url", List.of("jdbc:postgresql://127.0.0.1:15432/discord"));
        requireNonDefault(environment, failures, "spring.data.redis.host", List.of("127.0.0.1", "localhost"));
        rejectDefaultIfPresent(environment, failures, "spring.data.redis.password", List.of("dev_password"));

        if (!failures.isEmpty()) {
            throw new IllegalStateException("production secret/config validation failed: " + String.join(", ", failures));
        }
    }

    private static void requireSecret(
        Environment environment,
        List<String> failures,
        String key,
        List<String> forbiddenValues
    ) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            failures.add(key + " is required");
            return;
        }
        if (value.length() < MIN_SECRET_LENGTH) {
            failures.add(key + " must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        if (forbiddenValues.contains(value)) {
            failures.add(key + " must not use development/test defaults");
        }
    }

    private static void requireNonDefault(
        Environment environment,
        List<String> failures,
        String key,
        List<String> forbiddenValues
    ) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            failures.add(key + " is required");
            return;
        }
        if (forbiddenValues.contains(value)) {
            failures.add(key + " must not use development/test defaults");
        }
    }

    private static void rejectDefaultIfPresent(
        Environment environment,
        List<String> failures,
        String key,
        List<String> forbiddenValues
    ) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            return;
        }
        if (forbiddenValues.contains(value)) {
            failures.add(key + " must not use development/test defaults");
        }
    }
}
