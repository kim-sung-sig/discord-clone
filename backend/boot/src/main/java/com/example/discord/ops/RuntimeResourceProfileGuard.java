package com.example.discord.ops;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
final class RuntimeResourceProfileGuard {
    RuntimeResourceProfileGuard(Environment environment) {
        validateProfiles(environment);
    }

    static void validateProfiles(Environment environment) {
        boolean productionLike = environment.acceptsProfiles(Profiles.of("production", "admin-cli"));
        boolean kafkaEnabled = environment.acceptsProfiles(Profiles.of("kafka"));
        boolean testEnabled = environment.acceptsProfiles(Profiles.of("test"));
        boolean postgresEnabled = environment.acceptsProfiles(Profiles.of("postgres"));
        if (productionLike && !postgresEnabled) {
            throw new IllegalStateException(
                "production-like runtime profiles require postgres to avoid in-memory persistence defaults"
            );
        }
        if (kafkaEnabled && !testEnabled && !postgresEnabled) {
            throw new IllegalStateException(
                "kafka runtime profile requires postgres to avoid in-memory inbox and no-op outbox"
            );
        }
    }
}
