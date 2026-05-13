package com.example.discord.gateway;

import com.example.discord.guild.InMemoryGuildService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class GatewayConfiguration {
    @Bean
    InMemoryGatewayService gatewayService(InMemoryGuildService guildService, Clock authClock) {
        return new InMemoryGatewayService(guildService, authClock, Duration.ofSeconds(30));
    }
}
