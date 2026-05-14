package com.example.discord.experience;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InMemoryExperienceService {
    private final Map<UUID, StageSession> stageSessions = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeStageSessionIdsByChannel = new LinkedHashMap<>();
    private final Map<UUID, SoundboardSound> sounds = new LinkedHashMap<>();
    private final List<SoundboardPlayEvent> playEvents = new ArrayList<>();
    private final List<Entitlement> entitlements = new ArrayList<>();

    public synchronized StageSession startStageSession(UUID guildId, UUID channelId, String topic, UUID moderatorId) {
        requireUuid(guildId, "guildId");
        requireUuid(channelId, "channelId");
        requireText(topic, "topic");
        requireUuid(moderatorId, "moderatorId");

        StageSession session = new StageSession(
            UUID.randomUUID(),
            guildId,
            channelId,
            topic,
            Set.of(moderatorId),
            Set.of(),
            Set.of(),
            Set.of()
        );
        stageSessions.put(session.id(), session);
        activeStageSessionIdsByChannel.put(channelId, session.id());
        return session;
    }

    public synchronized StageSession requestToSpeak(UUID sessionId, UUID userId) {
        StageSession current = stageSession(sessionId);
        requireUuid(userId, "userId");

        LinkedHashSet<UUID> audienceIds = orderedSet(current.audienceIds());
        LinkedHashSet<UUID> pendingSpeakerIds = orderedSet(current.pendingSpeakerIds());
        LinkedHashSet<UUID> speakerIds = orderedSet(current.speakerIds());
        speakerIds.remove(userId);
        audienceIds.add(userId);
        pendingSpeakerIds.add(userId);
        return replace(current, speakerIds, audienceIds, pendingSpeakerIds);
    }

    public synchronized StageSession approveSpeaker(UUID sessionId, UUID userId) {
        StageSession current = stageSession(sessionId);
        requireUuid(userId, "userId");
        if (!current.pendingSpeakerIds().contains(userId)) {
            throw new IllegalStateException("speaker approval requires pending request");
        }

        LinkedHashSet<UUID> speakerIds = orderedSet(current.speakerIds());
        LinkedHashSet<UUID> audienceIds = orderedSet(current.audienceIds());
        LinkedHashSet<UUID> pendingSpeakerIds = orderedSet(current.pendingSpeakerIds());
        speakerIds.add(userId);
        audienceIds.remove(userId);
        pendingSpeakerIds.remove(userId);
        return replace(current, speakerIds, audienceIds, pendingSpeakerIds);
    }

    public synchronized StageSession moveToAudience(UUID sessionId, UUID userId) {
        StageSession current = stageSession(sessionId);
        requireUuid(userId, "userId");

        LinkedHashSet<UUID> speakerIds = orderedSet(current.speakerIds());
        LinkedHashSet<UUID> audienceIds = orderedSet(current.audienceIds());
        LinkedHashSet<UUID> pendingSpeakerIds = orderedSet(current.pendingSpeakerIds());
        speakerIds.remove(userId);
        pendingSpeakerIds.remove(userId);
        audienceIds.add(userId);
        return replace(current, speakerIds, audienceIds, pendingSpeakerIds);
    }

    public synchronized void requireSpeaker(UUID sessionId, UUID userId) {
        if (!stageSession(sessionId).speakerIds().contains(userId)) {
            throw new IllegalStateException("speaker role required");
        }
    }

    public synchronized StageSession stageSession(UUID sessionId) {
        requireUuid(sessionId, "sessionId");
        StageSession session = stageSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("stage session not found");
        }
        return session;
    }

    public synchronized StageSession activeStageSession(UUID channelId) {
        requireUuid(channelId, "channelId");
        UUID sessionId = activeStageSessionIdsByChannel.get(channelId);
        if (sessionId == null) {
            throw new IllegalArgumentException("stage session not found");
        }
        return stageSessions.get(sessionId);
    }

    public synchronized SoundboardSound registerSound(UUID guildId, String name, String objectKey, UUID creatorId) {
        requireUuid(guildId, "guildId");
        requireText(name, "sound name");
        requireText(objectKey, "objectKey");
        requireUuid(creatorId, "creatorId");

        SoundboardSound sound = new SoundboardSound(UUID.randomUUID(), guildId, name, objectKey, creatorId);
        sounds.put(sound.id(), sound);
        return sound;
    }

    public synchronized List<SoundboardSound> sounds(UUID guildId) {
        requireUuid(guildId, "guildId");
        return sounds.values().stream()
            .filter(sound -> sound.guildId().equals(guildId))
            .sorted(Comparator.comparing(SoundboardSound::name))
            .toList();
    }

    public synchronized SoundboardSound sound(UUID soundId) {
        requireUuid(soundId, "soundId");
        SoundboardSound sound = sounds.get(soundId);
        if (sound == null) {
            throw new IllegalArgumentException("sound not found");
        }
        return sound;
    }

    public synchronized SoundboardPlayEvent playSound(UUID channelId, UUID soundId, UUID userId) {
        requireUuid(channelId, "channelId");
        sound(soundId);
        requireUuid(userId, "userId");

        SoundboardPlayEvent event = new SoundboardPlayEvent(UUID.randomUUID(), channelId, soundId, userId);
        playEvents.add(event);
        return event;
    }

    public synchronized Entitlement grantEntitlement(UUID userId, UUID guildId, String featureKey) {
        requireUuid(userId, "userId");
        requireUuid(guildId, "guildId");
        requireText(featureKey, "featureKey");

        Entitlement entitlement = new Entitlement(UUID.randomUUID(), userId, guildId, featureKey);
        entitlements.add(entitlement);
        return entitlement;
    }

    public synchronized boolean hasEntitlement(UUID userId, String featureKey) {
        requireUuid(userId, "userId");
        requireText(featureKey, "featureKey");
        return entitlements.stream()
            .anyMatch(entitlement -> entitlement.userId().equals(userId) && entitlement.featureKey().equals(featureKey));
    }

    public List<CatalogItem> catalog() {
        return List.of(
            new CatalogItem("premium-hd-streaming", PremiumFeature.HD_STREAMING.key(), "HD Streaming"),
            new CatalogItem("premium-soundboard", PremiumFeature.PREMIUM_SOUNDBOARD.key(), "Premium Soundboard")
        );
    }

    public List<Quest> quests() {
        return List.of(
            new Quest("first-stage", "Host a stage session", PremiumFeature.QUEST_REWARDS.key())
        );
    }

    private StageSession replace(
        StageSession current,
        Set<UUID> speakerIds,
        Set<UUID> audienceIds,
        Set<UUID> pendingSpeakerIds
    ) {
        StageSession updated = new StageSession(
            current.id(),
            current.guildId(),
            current.channelId(),
            current.topic(),
            current.moderatorIds(),
            speakerIds,
            audienceIds,
            pendingSpeakerIds
        );
        stageSessions.put(updated.id(), updated);
        return updated;
    }

    private static LinkedHashSet<UUID> orderedSet(Set<UUID> values) {
        return new LinkedHashSet<>(values);
    }

    private static void requireUuid(UUID value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
