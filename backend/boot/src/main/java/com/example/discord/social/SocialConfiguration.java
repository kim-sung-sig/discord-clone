package com.example.discord.social;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SocialConfiguration {
    @Bean
    InMemorySocialService socialService() {
        return new InMemorySocialService();
    }
}
