package com.example.discord.gateway;

import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.permission.AuthorizationProjectionStore;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class GatewayConfiguration {
    @Bean
    @Profile("!redis & !kafka")
    GatewayEventBus gatewayEventBus(Clock authClock) {
        return new InMemoryGatewayEventBus(authClock);
    }

    @Bean
    @Profile("!redis")
    GatewaySessionRegistry gatewaySessionRegistry() {
        return new InMemoryGatewaySessionRegistry();
    }

    @Bean
    @Profile("!postgres")
    GatewayEventLog gatewayEventLog() {
        return new InMemoryGatewayEventLog();
    }

    @Bean
    @Profile("!postgres")
    GatewaySessionCursorStore gatewaySessionCursorStore() {
        return new InMemoryGatewaySessionCursorStore();
    }

    @Bean
    InMemoryGatewayService gatewayService(
        InMemoryGuildService guildService,
        Clock authClock,
        @Value("${discord.gateway.heartbeat-timeout-ms:30000}") long heartbeatTimeoutMillis,
        GatewayEventBus gatewayEventBus,
        GatewaySessionRegistry gatewaySessionRegistry,
        GatewayEventLog gatewayEventLog,
        GatewaySessionCursorStore gatewaySessionCursorStore,
        ObjectProvider<AuthorizationProjectionStore> authorizationProjections,
        @Value("${discord.authz.projection-enabled:false}") boolean projectionEnabled
    ) {
        return new InMemoryGatewayService(
            guildService,
            authClock,
            Duration.ofMillis(heartbeatTimeoutMillis),
            gatewayEventBus,
            gatewaySessionRegistry,
            authorizationProjections.getIfAvailable(),
            projectionEnabled,
            gatewayEventLog,
            gatewaySessionCursorStore
        );
    }
}
