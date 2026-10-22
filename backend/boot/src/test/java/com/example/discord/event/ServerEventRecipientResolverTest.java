package com.example.discord.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.discord.channel.ChannelType;
import com.example.discord.guild.Guild;
import com.example.discord.guild.GuildService;
import com.example.discord.guild.GuildStore;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ServerEventRecipientResolverTest {
    @Test
    void resolvesVisibleMembersFromOneGuildReadRegardlessOfMemberCount() {
        UUID ownerId = UUID.randomUUID();
        InMemoryGuildService seed = new InMemoryGuildService();
        var guild = seed.createGuild("events", ownerId);
        List<UUID> members = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID memberId = UUID.randomUUID();
            seed.addMember(guild.id(), memberId);
            members.add(memberId);
        }
        var stage = seed.createChannel(guild.id(), "stage", ChannelType.GUILD_TEXT, null);
        UUID viewers = seed.createRole(guild.id(), "viewers").id();
        seed.addChannelRoleOverwrite(guild.id(), stage.id(), guild.everyoneRole().id(),
            PermissionSet.empty(), PermissionSet.empty().grant(Permission.VIEW_CHANNEL));
        seed.addChannelRoleOverwrite(guild.id(), stage.id(), viewers,
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL), PermissionSet.empty());
        Set<UUID> expected = new HashSet<>(Set.of(ownerId));
        for (UUID memberId : members.subList(0, 10)) {
            seed.assignRoleToMember(guild.id(), memberId, viewers);
            expected.add(memberId);
        }
        SnapshotStore store = new SnapshotStore(seed.guild(guild.id()));
        ServerEvent event = new ServerEvent(UUID.randomUUID(), guild.id(), stage.id(), ownerId, "launch",
            Instant.EPOCH, Instant.EPOCH.plusSeconds(60), ServerEventStatus.SCHEDULED, List.of(), Instant.EPOCH);

        assertThat(new ServerEventRecipientResolver(new GuildService(store)).apply(event)).isEqualTo(expected);
        assertThat(store.reads).isEqualTo(1);
    }

    @Test
    void missingGuildStillFailsInsteadOfResolvingNoRecipients() {
        GuildService guilds = new InMemoryGuildService();
        ServerEvent event = new ServerEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "orphan", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), ServerEventStatus.SCHEDULED, List.of(), Instant.EPOCH);

        assertThatThrownBy(() -> new ServerEventRecipientResolver(guilds).apply(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("guild not found");
    }

    private static final class SnapshotStore implements GuildStore {
        private final Guild snapshot;
        private int reads;

        private SnapshotStore(Guild snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Guild get(UUID guildId) {
            reads++;
            return snapshot;
        }

        @Override
        public void create(Guild guild) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T update(UUID guildId, Function<Guild, T> mutation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UUID> guildIdsForMember(UUID memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID guildIdForChannel(UUID channelId) {
            throw new UnsupportedOperationException();
        }
    }
}
