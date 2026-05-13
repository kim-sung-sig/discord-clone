package com.example.discord.invite;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class InviteConfiguration {
    @Bean
    InMemoryInviteService inviteService(Clock authClock) {
        return new InMemoryInviteService(authClock);
    }
}
