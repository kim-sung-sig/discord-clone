package com.example.discord.message;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MessageConfiguration {
    @Bean
    InMemoryMessageService messageService() {
        return new InMemoryMessageService();
    }
}
