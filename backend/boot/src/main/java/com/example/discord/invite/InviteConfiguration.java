package com.example.discord.invite;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class InviteConfiguration {
    @Bean
    @Profile("test")
    InviteService inMemoryInviteService(Clock authClock) {
        return new InMemoryInviteService(authClock);
    }

    @Bean
    @Profile("postgres")
    InviteService jdbcInviteService(javax.sql.DataSource dataSource, Clock authClock) {
        return new JdbcInviteService(dataSource, authClock);
    }
}
