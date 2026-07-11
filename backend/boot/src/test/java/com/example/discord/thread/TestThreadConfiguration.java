package com.example.discord.thread;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("test")
class TestThreadConfiguration {
    @Bean
    ThreadService threadService() {
        return new InMemoryThreadService();
    }
}
