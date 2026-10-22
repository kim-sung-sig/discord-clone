package com.example.discord.moderation;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

@Configuration
class ModerationConfiguration {
    @Bean
    @Profile("postgres")
    @DependsOn("postgresFlyway")
    ModerationService moderationService(DataSource dataSource) {
        return new JdbcModerationService(dataSource);
    }
}
