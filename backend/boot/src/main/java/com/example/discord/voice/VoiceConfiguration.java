package com.example.discord.voice;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class VoiceConfiguration {
    @Bean
    InMemoryVoiceService voiceService() {
        return new InMemoryVoiceService(Clock.systemUTC());
    }
}
