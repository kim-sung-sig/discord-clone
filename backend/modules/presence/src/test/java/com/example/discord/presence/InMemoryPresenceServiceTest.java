package com.example.discord.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryPresenceServiceTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-05-14T00:00:00Z"));
    private final InMemoryPresenceService service = new InMemoryPresenceService(
        new InMemoryPresenceTtlStore(clock),
        clock
    );

    @Test
    void presenceStatusExpiresToOfflineAfterTtl() {
        service.updatePresence(USER_ID, PresenceStatus.ONLINE, Duration.ofSeconds(60));

        clock.advance(Duration.ofSeconds(59));
        assertThat(service.presence(USER_ID).status()).isEqualTo(PresenceStatus.ONLINE);

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.presence(USER_ID).status()).isEqualTo(PresenceStatus.OFFLINE);
    }

    @Test
    void typingIndicatorExpiresAfterTtl() {
        service.startTyping(CHANNEL_ID, USER_ID, Duration.ofSeconds(5));
        service.startTyping(CHANNEL_ID, OTHER_USER_ID, Duration.ofSeconds(20));

        clock.advance(Duration.ofSeconds(5));

        assertThat(service.typingUsers(CHANNEL_ID)).containsExactly(OTHER_USER_ID);
    }

    @Test
    void unreadCountIsDeterministicFromReadMarkerAndAuthoredSequences() {
        service.markRead(CHANNEL_ID, USER_ID, 42L);

        long unread = service.unreadCount(CHANNEL_ID, USER_ID, 50L, List.of(45L, 47L, 47L, 60L));

        assertThat(unread).isEqualTo(6L);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
