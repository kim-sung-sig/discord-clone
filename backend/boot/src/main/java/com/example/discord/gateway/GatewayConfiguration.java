package com.example.discord.gateway;

import com.example.discord.guild.InMemoryGuildService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class GatewayConfiguration {
    @Bean
    @Profile("!redis")
    GatewayEventBus gatewayEventBus(Clock authClock) {
        return new InMemoryGatewayEventBus(authClock);
    }

    @Bean
    InMemoryGatewayService gatewayService(
        InMemoryGuildService guildService,
        Clock authClock,
        @Value("${discord.gateway.heartbeat-timeout-ms:30000}") long heartbeatTimeoutMillis,
        GatewayEventBus gatewayEventBus
    ) {
        return new InMemoryGatewayService(guildService, authClock, Duration.ofMillis(heartbeatTimeoutMillis), gatewayEventBus);
    }
}
