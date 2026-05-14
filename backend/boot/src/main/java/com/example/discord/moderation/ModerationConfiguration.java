package com.example.discord.moderation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ModerationConfiguration {
    @Bean
    InMemoryModerationService moderationService() {
        return new InMemoryModerationService();
    }
}
