package com.example.discord.guild;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class GuildConfiguration implements WebMvcConfigurer {
    private final GuildMutationAuthenticationInterceptor guildMutationAuthenticationInterceptor;

    GuildConfiguration(GuildMutationAuthenticationInterceptor guildMutationAuthenticationInterceptor) {
        this.guildMutationAuthenticationInterceptor = guildMutationAuthenticationInterceptor;
    }

    @Bean
    InMemoryGuildService guildService() {
        return new InMemoryGuildService();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guildMutationAuthenticationInterceptor)
            .addPathPatterns("/api/guilds/**");
    }
}
