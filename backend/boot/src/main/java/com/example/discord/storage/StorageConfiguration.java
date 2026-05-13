package com.example.discord.storage;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class StorageConfiguration {
    @Bean
    InMemoryObjectStore objectStore() {
        return new InMemoryObjectStore();
    }

    @Bean
    InMemoryAttachmentService attachmentService(InMemoryObjectStore objectStore) {
        AttachmentUploadPolicy policy = new AttachmentUploadPolicy(
            8 * 1024 * 1024,
            Set.of("image/png", "image/jpeg", "image/gif", "image/webp"),
            Duration.ofNanos(1)
        );
        return new InMemoryAttachmentService(policy, objectStore, Clock.systemUTC());
    }
}
