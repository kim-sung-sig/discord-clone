package com.example.discord.event;

import static org.mockito.Mockito.mock;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.GuildService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Registers the postgres-only controller so the OpenAPI export documents it without a database. */
@Configuration(proxyBeanMethods = false)
@Profile("openapi-export")
class ServerEventOpenApiExportConfiguration {
    @Bean
    ServerEventController serverEventController(AuthenticatedUserResolver users, GuildService guilds) {
        return new ServerEventController(users, guilds, mock(JdbcServerEventService.class));
    }

}
