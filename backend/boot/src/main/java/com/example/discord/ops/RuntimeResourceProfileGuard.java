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
        boolean postgresEnabled = environment.acceptsProfiles(Profiles.of("postgres"));
        if (productionLike && !postgresEnabled) {
            throw new IllegalStateException(
                "production-like runtime profiles require postgres to avoid in-memory persistence defaults"
            );
        }
    }
}
