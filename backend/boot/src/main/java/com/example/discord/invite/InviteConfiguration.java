package com.example.discord.invite;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class InviteConfiguration {
    @Bean
    @Profile("!postgres & !production & !admin-cli")
    InMemoryInviteService inMemoryInviteService(Clock authClock) {
        return new InMemoryInviteService(authClock);
    }

    @Bean
    @Profile("postgres")
    InMemoryInviteService persistentInviteService(InviteSnapshotStore snapshots, Clock authClock) {
        return new PersistentInviteService(snapshots, authClock);
    }
}
