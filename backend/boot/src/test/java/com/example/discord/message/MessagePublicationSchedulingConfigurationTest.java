package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

class MessagePublicationSchedulingConfigurationTest {
    @Test
    void unqualifiedScheduledWorkKeepsRunningWhileLagPollingIsBlocked() {
        new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=kafka")
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(SchedulingEnabled.class, MessagePublicationSchedulingConfiguration.class)
            .withBean(SchedulingProbe.class, SchedulingProbe::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                SchedulingProbe probe = context.getBean(SchedulingProbe.class);
                try {
                    assertThat(probe.lagBlocked.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(probe.relayRan.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(probe.lagThread.get()).startsWith("message-publication-lag-");
                    assertThat(probe.relayThread.get()).startsWith("scheduling-");
                } finally {
                    probe.release.countDown();
                }
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingEnabled {
    }

    static class SchedulingProbe {
        private final CountDownLatch lagBlocked = new CountDownLatch(1);
        private final CountDownLatch relayRan = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<String> lagThread = new AtomicReference<>();
        private final AtomicReference<String> relayThread = new AtomicReference<>();

        @Scheduled(fixedDelay = 10, scheduler = "messagePublicationLagScheduler")
        void blockLagPolling() throws InterruptedException {
            lagThread.compareAndSet(null, Thread.currentThread().getName());
            lagBlocked.countDown();
            release.await(10, TimeUnit.SECONDS);
        }

        @Scheduled(fixedDelay = 10)
        void relay() {
            relayThread.compareAndSet(null, Thread.currentThread().getName());
            relayRan.countDown();
        }
    }
}
