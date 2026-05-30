package com.example.discord.message;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class MessageConfiguration {
    @Bean
    @Profile("!postgres & !production & !admin-cli")
    InMemoryMessageService inMemoryMessageService() {
        return new InMemoryMessageService();
    }

    @Bean
    @Profile("postgres")
    InMemoryMessageService persistentMessageService(MessageSnapshotStore snapshots) {
        return new PersistentMessageService(snapshots);
    }
}
