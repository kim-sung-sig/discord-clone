package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisGatewayEventBusTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("rawtypes")
    private final StreamOperations streamOperations = mock(StreamOperations.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-17T00:00:00Z"), ZoneOffset.UTC);
    private final RedisGatewayEventBus eventBus = new RedisGatewayEventBus(redis, new ObjectMapper(), clock, "node-a");

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
}
