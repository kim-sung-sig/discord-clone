package com.example.discord.message;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@Profile("kafka")
class MessagePublicationSchedulingConfiguration {
    // Any TaskScheduler bean makes Boot back off its default scheduler, and Spring would then route
    // unqualified @Scheduled work (Outbox relay, gateway maintenance) onto the lag poller's thread.
    // Mirrors Boot's default scheduler (one thread, "scheduling-" prefix) without importing Boot types, so
    // spring.task.scheduling.*, TaskDecorator/customizer beans and virtual threads do not apply here.
    @Bean(name = "taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduling-");
        return scheduler;
    }

    @Bean(name = "messagePublicationLagScheduler")
    ThreadPoolTaskScheduler messagePublicationLagScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("message-publication-lag-");
        return scheduler;
    }
}
