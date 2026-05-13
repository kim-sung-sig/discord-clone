package com.example.discord.invite;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InMemoryInviteService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final Map<String, StoredInvite> invites = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public InMemoryInviteService(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }
        this.clock = clock;
    }

    public synchronized Invite create(CreateInviteCommand command) {
        StoredInvite invite = new StoredInvite(command, nextCode(), clock.instant());
        invites.put(invite.code, invite);
        return invite.snapshot();
    }

    public synchronized Invite preview(String code) {
        StoredInvite invite = requireInvite(code);
        requireUsable(invite);
        return invite.snapshot();
    }

    public synchronized Invite get(String code) {
        return requireInvite(code).snapshot();
    }

    public synchronized InviteAcceptResult accept(String code, UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId is required");
        }
        StoredInvite invite = requireInvite(code);
        requireUsable(invite);
        boolean alreadyAccepted = invite.acceptedMemberIds.contains(memberId);
        if (!alreadyAccepted && invite.maxUses > 0 && invite.acceptedMemberIds.size() >= invite.maxUses) {
            throw new InviteMaxUsesExceededException();
        }
        if (!alreadyAccepted) {
            invite.acceptedMemberIds.add(memberId);
        }
        return new InviteAcceptResult(invite.snapshot(), memberId, alreadyAccepted);
    }

    public synchronized Invite delete(String code) {
        StoredInvite invite = requireInvite(code);
        if (invite.deletedAt == null) {
            invite.deletedAt = clock.instant();
        }
        return invite.snapshot();
    }

    private void requireUsable(StoredInvite invite) {
        if (invite.deletedAt != null) {
            throw new InviteDeletedException();
        }
        if (isExpired(invite)) {
            throw new InviteExpiredException();
        }
    }

    private boolean isExpired(StoredInvite invite) {
        if (invite.maxAgeSeconds == 0) {
            return false;
        }
        return !clock.instant().isBefore(invite.createdAt.plusSeconds(invite.maxAgeSeconds));
    }

    private StoredInvite requireInvite(String code) {
        StoredInvite invite = invites.get(code);
        if (invite == null) {
            throw new InviteNotFoundException();
        }
        return invite;
    }

    private String nextCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
            }
            code = builder.toString();
        } while (invites.containsKey(code));
        return code;
    }

    private static final class StoredInvite {
        private final String code;
        private final UUID guildId;
        private final UUID channelId;
        private final UUID creatorId;
        private final long maxAgeSeconds;
        private final int maxUses;
        private final boolean temporary;
        private final java.util.List<UUID> roleGrantIds;
        private final Instant createdAt;
        private final Set<UUID> acceptedMemberIds = new LinkedHashSet<>();
        private Instant deletedAt;

        private StoredInvite(CreateInviteCommand command, String code, Instant createdAt) {
            this.code = code;
            this.guildId = command.guildId();
            this.channelId = command.channelId();
            this.creatorId = command.creatorId();
            this.maxAgeSeconds = command.maxAgeSeconds();
            this.maxUses = command.maxUses();
            this.temporary = command.temporary();
            this.roleGrantIds = command.roleGrantIds();
            this.createdAt = createdAt;
        }

        private Invite snapshot() {
            return new Invite(
                code,
                guildId,
                channelId,
                creatorId,
                maxAgeSeconds,
                maxUses,
                temporary,
                roleGrantIds,
                createdAt,
                deletedAt,
                acceptedMemberIds
            );
        }
    }
}
