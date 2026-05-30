package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisGatewaySessionRegistryTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("rawtypes")
    private final HashOperations hashOperations = mock(HashOperations.class);
    private final ValueOperations<String, String> valueOperations = mock();
    private final SetOperations<String, String> setOperations = mock();
    private final RedisGatewaySessionRegistry registry =
        new RedisGatewaySessionRegistry(redis, new ObjectMapper(), "gateway:sessions:test", 86_400L);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redis.opsForHash()).thenReturn(hashOperations);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void saveStoresSecretFreeSessionMetadataInRedisHash() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID guildId = UUID.randomUUID();
        GatewaySession session = new GatewaySession(
            sessionId,
            userId,
            Set.of(guildId),
            Instant.parse("2026-05-21T00:00:00Z"),
            false,
            42L
        );

        registry.save(session);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("gateway:sessions:test:" + sessionId), valueCaptor.capture(), eq(Duration.ofHours(24)));
        verify(setOperations).add("gateway:sessions:test", sessionId.toString());
        verify(redis).expire("gateway:sessions:test", Duration.ofHours(24));
        assertThat(valueCaptor.getValue())
            .contains(sessionId.toString())
            .contains(userId.toString())
            .contains(guildId.toString())
            .contains("\"lastDeliveredSequence\":42")
            .doesNotContain("accessToken")
            .doesNotContain("refreshToken")
            .doesNotContain("LIVEKIT")
            .doesNotContain("password");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findReadsSessionMetadataFromRedisHash() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID guildId = UUID.randomUUID();
        String encoded = """
            {
              "id": "%s",
              "userId": "%s",
              "guildIds": ["%s"],
              "lastAcknowledgedAt": "2026-05-21T00:00:00Z",
              "closed": false,
              "lastDeliveredSequence": 7
            }
            """.formatted(sessionId, userId, guildId);
        when(valueOperations.get("gateway:sessions:test:" + sessionId)).thenReturn(encoded);

        GatewaySession session = registry.find(sessionId).orElseThrow();

        assertThat(session.id()).isEqualTo(sessionId);
        assertThat(session.userId()).isEqualTo(userId);
        assertThat(session.guildIds()).containsExactly(guildId);
        assertThat(session.lastDeliveredSequence()).isEqualTo(7L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sessionsReadsAllSessionMetadataFromRedisHash() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(setOperations.members("gateway:sessions:test")).thenReturn(Set.of(first.toString(), second.toString()));
        when(valueOperations.get("gateway:sessions:test:" + first)).thenReturn(encoded(first, 1L));
        when(valueOperations.get("gateway:sessions:test:" + second)).thenReturn(encoded(second, 2L));

        assertThat(registry.sessions())
            .extracting(GatewaySession::lastDeliveredSequence)
            .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void sessionsPrunesIndexEntriesWhoseSessionKeyExpired() {
        UUID expired = UUID.randomUUID();
        when(setOperations.members("gateway:sessions:test")).thenReturn(Set.of(expired.toString()));
        when(valueOperations.get("gateway:sessions:test:" + expired)).thenReturn(null);

        assertThat(registry.sessions()).isEmpty();

        verify(setOperations).remove("gateway:sessions:test", expired.toString());
    }

    @Test
    void sessionsPrunesMalformedSessionMetadata() {
        UUID malformed = UUID.randomUUID();
        when(setOperations.members("gateway:sessions:test")).thenReturn(Set.of(malformed.toString()));
        when(valueOperations.get("gateway:sessions:test:" + malformed)).thenReturn("{\"accessToken\":\"secret\"");

        assertThat(registry.sessions()).isEmpty();

        verify(setOperations).remove("gateway:sessions:test", malformed.toString());
        verify(redis).delete("gateway:sessions:test:" + malformed);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findPrunesMalformedSessionMetadataWithoutReturningIt() {
        UUID malformed = UUID.randomUUID();
        when(valueOperations.get("gateway:sessions:test:" + malformed)).thenReturn("{\"password\":\"secret\"");

        assertThat(registry.find(malformed)).isEmpty();

        verify(setOperations).remove("gateway:sessions:test", malformed.toString());
        verify(redis).delete("gateway:sessions:test:" + malformed);
        verify(hashOperations, never()).get("gateway:sessions:test", malformed.toString());
    }

    private static String encoded(UUID sessionId, long sequence) {
        return """
            {
              "id": "%s",
              "userId": "%s",
              "guildIds": [],
              "lastAcknowledgedAt": "2026-05-21T00:00:00Z",
              "closed": false,
              "lastDeliveredSequence": %d
            }
            """.formatted(sessionId, UUID.randomUUID(), sequence);
    }
}
