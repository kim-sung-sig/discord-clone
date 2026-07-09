package com.example.discord.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryExperienceServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-15T00:00:00Z");

    private final InMemoryExperienceService service = new InMemoryExperienceService(
        Clock.fixed(NOW, ZoneOffset.UTC),
        new InMemoryEntitlementStore()
    );

    @Test
    void audienceRequestCannotSpeakBeforeModeratorApproval() {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        UUID audienceId = UUID.randomUUID();

        StageSession session = service.startStageSession(guildId, channelId, "Town hall", moderatorId);
        StageSession requested = service.requestToSpeak(session.id(), audienceId);

        assertThat(requested.audienceIds()).contains(audienceId);
        assertThat(requested.pendingSpeakerIds()).contains(audienceId);
        assertThat(requested.speakerIds()).doesNotContain(audienceId);
        assertThatThrownBy(() -> service.requireSpeaker(session.id(), audienceId))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approvalMovesAudienceRequestToSpeaker() {
        StageSession session = service.startStageSession(UUID.randomUUID(), UUID.randomUUID(), "Office hours", UUID.randomUUID());
        UUID audienceId = UUID.randomUUID();

        service.requestToSpeak(session.id(), audienceId);
        StageSession approved = service.approveSpeaker(session.id(), audienceId);

        assertThat(approved.speakerIds()).contains(audienceId);
        assertThat(approved.pendingSpeakerIds()).doesNotContain(audienceId);
        assertThat(approved.audienceIds()).doesNotContain(audienceId);
    }

    @Test
    void moveBackToAudienceRemovesSpeakerRole() {
        StageSession session = service.startStageSession(UUID.randomUUID(), UUID.randomUUID(), "Panel", UUID.randomUUID());
        UUID speakerId = UUID.randomUUID();

        service.requestToSpeak(session.id(), speakerId);
        service.approveSpeaker(session.id(), speakerId);
        StageSession audience = service.moveToAudience(session.id(), speakerId);

        assertThat(audience.audienceIds()).contains(speakerId);
        assertThat(audience.speakerIds()).doesNotContain(speakerId);
        assertThat(audience.pendingSpeakerIds()).doesNotContain(speakerId);
    }

    @Test
    void soundboardRegistersSoundAndPlayEvent() {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SoundboardSound sound = service.registerSound(guildId, "airhorn", "sounds/airhorn.ogg", userId);
        SoundboardPlayEvent event = service.playSound(channelId, sound.id(), userId);

        assertThat(service.sounds(guildId)).containsExactly(sound);
        assertThat(event.channelId()).isEqualTo(channelId);
        assertThat(event.soundId()).isEqualTo(sound.id());
        assertThat(event.userId()).isEqualTo(userId);
    }

}
