package com.example.discord.experience;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ExperienceConfiguration {
    @Bean
    InMemoryExperienceService experienceService() {
        return new InMemoryExperienceService();
    }
}
