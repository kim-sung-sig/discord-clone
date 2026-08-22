package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class MessageKafkaConfigurationTest {
    @Test
    void exhaustedRetryDlqContainsMetadataOnly() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "discord.message.published.v1",
            0,
            7L,
            "channel",
            "{\"eventId\":\"event-1\",\"content\":\"secret body\"}"
        );

        String deadLetter = MessageKafkaConfiguration.deadLetter(
            new ObjectMapper(),
            record,
            new IllegalStateException("secret exception")
        );

        assertThat(deadLetter)
            .contains("event-1", "payloadSha256Prefix", "discord.message.published.v1")
            .doesNotContain("secret body", "content", "secret exception");
    }

    @Test
    void retryHandlerIsProvidedForKafkaProfile() {
        KafkaTemplate<String, String> kafka = mock();
        MessageKafkaConfiguration configuration = new MessageKafkaConfiguration();

        assertThat(configuration.messageKafkaErrorHandler(kafka, new ObjectMapper(), "discord", 1000))
            .isNotNull();
    }
}
