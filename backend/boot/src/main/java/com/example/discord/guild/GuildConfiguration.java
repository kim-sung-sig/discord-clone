package com.example.discord.guild;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class GuildConfiguration {
    @Bean
    InMemoryGuildService guildService() {
        return new InMemoryGuildService();
    }
}
