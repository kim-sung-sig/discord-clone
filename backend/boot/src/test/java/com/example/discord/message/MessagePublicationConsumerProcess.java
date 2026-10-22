package com.example.discord.message;

import com.example.discord.DiscordApplication;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test-only child JVM: blocks after the real Gateway write and before the real Inbox write. */
public final class MessagePublicationConsumerProcess {
    private MessagePublicationConsumerProcess() { }

    public static void main(String[] args) {
        SpringApplication.run(new Class<?>[] {DiscordApplication.class, CheckpointConfiguration.class}, args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CheckpointConfiguration {
        @Bean
        @Primary
        MessagePublicationInbox checkpointInbox(
            JdbcMessagePublicationInbox durable,
            @Value("${pipeline.process.event-id}") UUID expectedEventId,
            @Value("${pipeline.process.block}") boolean block
        ) {
            return new MessagePublicationInbox() {
                @Override
                public boolean isProcessed(UUID eventId) {
                    return durable.isProcessed(eventId);
                }

                @Override
                public void markProcessed(UUID eventId) {
                    if (expectedEventId.equals(eventId) && block) {
                        System.out.println("PIPELINE_PROCESS_BLOCKED " + eventId);
                        System.out.flush();
                        try {
                            new CountDownLatch(1).await();
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("consumer checkpoint interrupted", failure);
                        }
                    }
                    durable.markProcessed(eventId);
                    if (expectedEventId.equals(eventId)) {
                        System.out.println("PIPELINE_PROCESS_COMPLETED " + eventId);
                        System.out.flush();
                    }
                }
            };
        }
    }
}
