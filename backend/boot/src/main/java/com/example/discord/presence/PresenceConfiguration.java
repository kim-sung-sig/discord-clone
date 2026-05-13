package com.example.discord.presence;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PresenceConfiguration {
    @Bean
    InMemoryPresenceService presenceService() {
        Clock clock = Clock.systemUTC();
        return new InMemoryPresenceService(new InMemoryRedisPresenceTtlStore(clock), clock);
    }
}
