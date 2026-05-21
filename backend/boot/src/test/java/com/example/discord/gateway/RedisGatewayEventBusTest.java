package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisGatewayEventBusTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("rawtypes")
    private final StreamOperations streamOperations = mock(StreamOperations.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-17T00:00:00Z"), ZoneOffset.UTC);
    private final RedisGatewayEventBus eventBus =
        new RedisGatewayEventBus(redis, new ObjectMapper(), clock, "node-a", "discord-gateway", 1000L);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redis.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishWritesSanitizedEventToChannelStream() {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        GatewayBusEvent event = eventBus.publish(new GatewayBusPublishCommand(
            "MESSAGE_CREATE",
            guildId,
            channelId,
            Map.of(
                "content", "safe",
                "accessToken", "must-not-leak",
                "attachment", Map.of(
                    "id", "att-1",
                    "signedUrl", "https://cdn.example.test/file?X-Amz-Signature=secret"
                )
            )
        ));

        ArgumentCaptor<MapRecord> recordCaptor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        MapRecord record = recordCaptor.getValue();
        Map<?, ?> values = (Map<?, ?>) record.getValue();
        assertThat(record.getStream()).isEqualTo("gateway:channel:" + channelId);
        assertThat(values.get("type")).isEqualTo("MESSAGE_CREATE");
        assertThat(values.get("payload").toString())
            .contains("safe")
            .doesNotContain("must-not-leak")
            .doesNotContain("signedUrl")
            .doesNotContain("X-Amz-Signature");
        assertThat(event.payload())
            .containsEntry("content", "safe")
            .doesNotContainKey("accessToken");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pollUsesConsumerGroupPendingRecoveryAndAcknowledgesRecords() {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        MapRecord<String, String, String> pending = MapRecord.create(
            "gateway:channel:" + channelId,
            Map.of(
                "eventId", "evt-pending",
                "type", "MESSAGE_CREATE",
                "guildId", guildId.toString(),
                "channelId", channelId.toString(),
                "payload", "{\"content\":\"pending\"}",
                "createdAt", "2026-05-17T00:00:00Z"
            )
        ).withId(RecordId.of("1-0"));
        when(streamOperations.read(
            eq(Consumer.from("discord-gateway:node-a", "node-a")),
            any(),
            any()
        )).thenReturn(List.of(pending), List.of());
        eventBus.subscribeChannel(channelId);

        eventBus.pollOnce();

        verify(streamOperations).createGroup(
            "gateway:channel:" + channelId,
            ReadOffset.from("0-0"),
            "discord-gateway:node-a"
        );
        verify(streamOperations, Mockito.atLeastOnce()).read(
            eq(Consumer.from("discord-gateway:node-a", "node-a")),
            any(),
            any()
        );
        verify(streamOperations).acknowledge(
            "gateway:channel:" + channelId,
            "discord-gateway:node-a",
            RecordId.of("1-0")
        );
        assertThat(eventBus.metrics().processedTotal()).isEqualTo(1L);
        assertThat(eventBus.metrics().ackedTotal()).isEqualTo(1L);
        assertThat(eventBus.metrics().failedDecodeTotal()).isZero();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pollAcknowledgesOwnSourceRecordWithoutDuplicateLocalDelivery() {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        CopyOnWriteArrayList<GatewayBusEvent> received = new CopyOnWriteArrayList<>();
        eventBus.addEventListener(received::add);
        GatewayBusEvent published = eventBus.publish(new GatewayBusPublishCommand(
            "MESSAGE_CREATE",
            guildId,
            channelId,
            Map.of("content", "local first")
        ));
        MapRecord<String, String, String> ownRecord = MapRecord.create(
            "gateway:channel:" + channelId,
            Map.of(
                "eventId", published.eventId(),
                "type", "MESSAGE_CREATE",
                "guildId", guildId.toString(),
                "channelId", channelId.toString(),
                "payload", "{\"content\":\"local first\"}",
                "createdAt", "2026-05-17T00:00:00Z",
                "sourceNodeId", "node-a"
            )
        ).withId(RecordId.of("2-0"));
        when(streamOperations.read(
            eq(Consumer.from("discord-gateway:node-a", "node-a")),
            any(),
            any()
        )).thenReturn(List.of(ownRecord), List.of());
        eventBus.subscribeChannel(channelId);

        eventBus.pollOnce();

        assertThat(received).extracting(GatewayBusEvent::eventId).containsExactly(published.eventId());
        assertThat(eventBus.metrics().processedTotal()).isZero();
        assertThat(eventBus.metrics().ackedTotal()).isEqualTo(1L);
        verify(streamOperations).acknowledge(
            "gateway:channel:" + channelId,
            "discord-gateway:node-a",
            RecordId.of("2-0")
        );
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void publishTrimsStreamsToConfiguredRetentionLimit() {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        eventBus.publish(new GatewayBusPublishCommand("MESSAGE_CREATE", guildId, channelId, Map.of("content", "safe")));

        verify(streamOperations).trim("gateway:channel:" + channelId, 1000L);
        assertThat(eventBus.metrics().trimmedTotal()).isZero();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void malformedStreamRecordIsDeadLetteredWithoutRawPayloadLeak() {
        UUID channelId = UUID.randomUUID();
        MapRecord<String, String, String> malformed = MapRecord.create(
            "gateway:channel:" + channelId,
            Map.of(
                "eventId", "evt-bad",
                "type", "MESSAGE_CREATE",
                "guildId", "not-a-uuid",
                "channelId", channelId.toString(),
                "payload", "{\"accessToken\":\"secret-token\"",
                "createdAt", "not-an-instant"
            )
        ).withId(RecordId.of("2-0"));
        when(streamOperations.read(
            eq(Consumer.from("discord-gateway:node-a", "node-a")),
            any(),
            any()
        )).thenReturn(List.of(malformed), List.of());
        eventBus.subscribeChannel(channelId);

        eventBus.pollOnce();

        ArgumentCaptor<MapRecord> recordCaptor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        MapRecord deadLetter = recordCaptor.getValue();
        assertThat(deadLetter.getStream()).isEqualTo("gateway:dead-letter");
        assertThat(deadLetter.getValue().toString())
            .contains("MALFORMED_RECORD")
            .contains("messageSha256Prefix")
            .doesNotContain("secret-token")
            .doesNotContain("accessToken");
        assertThat(eventBus.deadLetterMetrics().total()).isEqualTo(1L);
        assertThat(eventBus.deadLetterMetrics().byReason()).containsEntry("MALFORMED_RECORD", 1L);
        assertThat(eventBus.deadLetterMetrics().alert().active()).isTrue();
        verify(streamOperations).trim("gateway:dead-letter", 1000L);
        verify(streamOperations).acknowledge("gateway:channel:" + channelId, "discord-gateway:node-a", RecordId.of("2-0"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listenerFailureIsDeadLetteredAndPollingContinues() {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        MapRecord<String, String, String> record = MapRecord.create(
            "gateway:channel:" + channelId,
            Map.of(
                "eventId", "evt-listener-failure",
                "type", "MESSAGE_CREATE",
                "guildId", guildId.toString(),
                "channelId", channelId.toString(),
                "payload", "{\"content\":\"should-not-enter-dlq\"}",
                "createdAt", "2026-05-17T00:00:00Z",
                "sourceNodeId", "node-b"
            )
        ).withId(RecordId.of("3-0"));
        when(streamOperations.read(
            eq(Consumer.from("discord-gateway:node-a", "node-a")),
            any(),
            any()
        )).thenReturn(List.of(record), List.of());
        eventBus.addEventListener(event -> {
            throw new IllegalStateException("listener failure with secret-token");
        });
        eventBus.subscribeChannel(channelId);

        assertThatCode(eventBus::pollOnce).doesNotThrowAnyException();

        ArgumentCaptor<MapRecord> recordCaptor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        MapRecord deadLetter = recordCaptor.getValue();
        assertThat(deadLetter.getStream()).isEqualTo("gateway:dead-letter");
        assertThat(deadLetter.getValue().toString())
            .contains("LISTENER_FAILURE")
            .contains("evt-listener-failure")
            .doesNotContain("should-not-enter-dlq")
            .doesNotContain("secret-token")
            .doesNotContain("listener failure");
        assertThat(eventBus.deadLetterMetrics().total()).isEqualTo(1L);
        assertThat(eventBus.deadLetterMetrics().byReason()).containsEntry("LISTENER_FAILURE", 1L);
        assertThat(eventBus.deadLetterMetrics().alert().active()).isTrue();
        verify(streamOperations).acknowledge("gateway:channel:" + channelId, "discord-gateway:node-a", RecordId.of("3-0"));
    }
}
