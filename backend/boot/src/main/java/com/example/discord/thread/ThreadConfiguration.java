package com.example.discord.thread;

import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class ThreadConfiguration {
    @Bean
    @Profile("postgres")
    ThreadService threadService(DataSource dataSource) {
        return new JdbcThreadService(dataSource, Clock.systemUTC());
    }
}
