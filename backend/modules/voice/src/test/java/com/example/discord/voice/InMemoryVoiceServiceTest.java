package com.example.discord.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryVoiceServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final Instant NOW = Instant.parse("2026-05-14T00:00:00Z");

    private final InMemoryVoiceService service = new InMemoryVoiceService(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void joinCreatesParticipantAndDeterministicNonProductionLiveKitSkeletonToken() {
        VoiceJoinResult result = service.join(GUILD_ID, CHANNEL_ID, USER_ID);

        assertThat(result.participant()).isEqualTo(new VoiceParticipantState(
            GUILD_ID,
            CHANNEL_ID,
            USER_ID,
            false,
            false,
            false,
            false,
            NOW
        ));
        assertThat(result.token().room()).isEqualTo("voice:%s:%s".formatted(GUILD_ID, CHANNEL_ID));
        assertThat(result.token().participant()).isEqualTo(USER_ID.toString());
        assertThat(result.token().provider()).isEqualTo("LIVEKIT_SKELETON");
        assertThat(result.token().token()).isEqualTo(
            "NON_PRODUCTION_LIVEKIT_SKELETON:%s:%s:%s".formatted(GUILD_ID, CHANNEL_ID, USER_ID)
        );
        assertThat(result.token().expiresAt()).isEqualTo(NOW.plusSeconds(900));
    }

    @Test
    void leaveRemovesParticipantState() {
        service.join(GUILD_ID, CHANNEL_ID, USER_ID);

        service.leave(GUILD_ID, CHANNEL_ID, USER_ID);

        assertThat(service.participants(GUILD_ID, CHANNEL_ID)).isEmpty();
    }

    @Test
    void stateUpdateMutatesParticipantAndRecordsVoiceStateEvent() {
        service.join(GUILD_ID, CHANNEL_ID, USER_ID);

        VoiceParticipantState updated = service.update(new VoiceStateUpdateCommand(
            GUILD_ID,
            CHANNEL_ID,
            USER_ID,
            true,
            true,
            true,
            true
        ));

        assertThat(updated.muted()).isTrue();
        assertThat(updated.deafened()).isTrue();
        assertThat(updated.speaking()).isTrue();
        assertThat(updated.screenSharing()).isTrue();
        assertThat(service.events()).last().isEqualTo(new VoiceStateEvent(
            "VOICE_STATE_UPDATE",
            GUILD_ID,
            CHANNEL_ID,
            USER_ID,
            true,
            true,
            true,
            true,
            NOW
        ));
    }
}
