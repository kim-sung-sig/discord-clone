package com.example.discord.presence;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class PresenceConfiguration {
    @Bean
    @Profile("!redis")
    PresenceTtlStore inMemoryPresenceTtlStore() {
        return new InMemoryPresenceTtlStore(Clock.systemUTC());
    }

    @Bean
    InMemoryPresenceService presenceService(PresenceTtlStore ttlStore) {
        return new InMemoryPresenceService(ttlStore, Clock.systemUTC());
    }
}
