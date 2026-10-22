package com.example.discord.message;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

final class MessagePublicationConsumerLag {
    private static final Logger log = LoggerFactory.getLogger(MessagePublicationConsumerLag.class);
    private static final String GROUP = "gateway-service-v1";
    private static final long ADMIN_TIMEOUT_SECONDS = 2;

    private final AdminClient kafka;
    private final String topic;
    private final AtomicLong lag = new AtomicLong();

    MessagePublicationConsumerLag(AdminClient kafka, MeterRegistry metrics, String topicPrefix) {
        this.kafka = kafka;
        this.topic = (topicPrefix == null || topicPrefix.isBlank() ? "discord" : topicPrefix.trim())
            + ".message.published.v1";
        Gauge.builder("discord.message.publication.consumer.lag", lag, AtomicLong::doubleValue)
            .description("Kafka latest offset minus committed offset for the message publication consumer")
            .tag("group", GROUP)
            .register(metrics);
    }

    @Scheduled(
        fixedDelayString = "${discord.kafka.consumer-lag-refresh-ms:1000}",
        scheduler = "messagePublicationLagScheduler"
    )
    void refresh() {
        try {
            Set<TopicPartition> topicPartitions = kafka.describeTopics(java.util.List.of(topic))
                .allTopicNames().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .get(topic).partitions().stream()
                .map(partition -> new TopicPartition(topic, partition.partition()))
                .collect(java.util.stream.Collectors.toSet());
            Map<TopicPartition, OffsetAndMetadata> committed = kafka.listConsumerGroupOffsets(GROUP)
                .partitionsToOffsetAndMetadata().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Map<TopicPartition, OffsetSpec> requests = new HashMap<>();
            topicPartitions.stream()
                .forEach(partition -> requests.put(partition, OffsetSpec.latest()));
            if (requests.isEmpty()) {
                lag.set(0);
                return;
            }
            Map<TopicPartition, Long> latest = new HashMap<>();
            kafka.listOffsets(requests).all().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .forEach((partition, offset) -> latest.put(partition, offset.offset()));
            lag.set(calculateLag(topicPartitions, committed, latest, topic));
        } catch (Exception failure) {
            log.warn("Unable to refresh message publication consumer lag reason={}", failure.getClass().getSimpleName());
        }
    }

    static long calculateLag(
        Set<TopicPartition> topicPartitions,
        Map<TopicPartition, OffsetAndMetadata> committed,
        Map<TopicPartition, Long> latest,
        String topic
    ) {
        return topicPartitions.stream()
            .filter(partition -> topic.equals(partition.topic()))
            .mapToLong(partition -> Math.max(0L,
                latest.getOrDefault(partition, 0L) - committed.getOrDefault(partition, new OffsetAndMetadata(0L)).offset()))
            .sum();
    }
}
