package com.example.discord.moderation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
class TestModerationConfiguration {
    @Bean
    @Profile("test & !postgres")
    ModerationService inMemoryModerationService() {
        return new InMemoryModerationService();
    }
}
