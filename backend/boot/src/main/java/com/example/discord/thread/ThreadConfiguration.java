package com.example.discord.thread;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ThreadConfiguration {
    @Bean
    InMemoryThreadService threadService() {
        return new InMemoryThreadService();
    }
}
