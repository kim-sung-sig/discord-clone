package com.example.discord.guild;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class GuildServicePermissionSnapshotTest {
    private static final UUID GUILD = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID CHANNEL = UUID.randomUUID();
    private static final UUID EVERYONE = UUID.randomUUID();
    private static final UUID REQUESTER_ROLE = UUID.randomUUID();
    private static final UUID TARGET_ROLE = UUID.randomUUID();

    @Test
    void sendDecisionCannotCombineViewFromOneSnapshotWithSendFromAnother() {
        assertChannelDecisionUsesOneSnapshot(Permission.SEND_MESSAGES);
    }

    @Test
    void manageDecisionCannotCombineViewFromOneSnapshotWithManageFromAnother() {
        assertChannelDecisionUsesOneSnapshot(Permission.MANAGE_MESSAGES);
    }

    @Test
    void assigningRoleUsesRequesterAndTargetPermissionsFromTheSameSnapshot() {
        SequencedStore store = roleStore();
        assertThat(new GuildService(store).canAssignRole(GUILD, MEMBER, TARGET_ROLE)).isFalse();
        assertThat(store.reads).isEqualTo(1);
    }

    @Test
    void assigningRolesUsesRequesterAndTargetPermissionsFromTheSameSnapshot() {
        SequencedStore store = roleStore();
        assertThat(new GuildService(store).canAssignRoles(GUILD, MEMBER, List.of(TARGET_ROLE))).isFalse();
        assertThat(store.reads).isEqualTo(1);
    }

    @Test
    void channelViewersAreDecidedFromTheGivenSnapshotWithoutRereading() {
        UUID outsider = UUID.randomUUID();
        SequencedStore store = new SequencedStore(
            guild(PermissionSet.empty().grant(Permission.VIEW_CHANNEL), PermissionSet.empty(), PermissionSet.empty()),
            guild(PermissionSet.empty(), PermissionSet.empty(), PermissionSet.empty())
        );
        GuildService service = new GuildService(store);

        Guild snapshot = service.guild(GUILD);

        assertThat(service.channelViewers(snapshot, CHANNEL, List.of(MEMBER, OWNER, outsider)))
            .containsExactlyInAnyOrder(MEMBER, OWNER);
        assertThat(service.channelViewers(snapshot, UUID.randomUUID(), List.of(OWNER, MEMBER))).containsExactly(OWNER);
        assertThat(service.channelViewers(snapshot, CHANNEL, java.util.Arrays.asList(MEMBER, null)))
            .containsExactly(MEMBER);
        assertThat(store.reads).isEqualTo(1);
    }

    @Test
    void completePermissionsFromOneSnapshotAllowOperations() {
        PermissionSet channelPermissions = PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
            .grant(Permission.SEND_MESSAGES).grant(Permission.MANAGE_MESSAGES);
        PermissionSet rolePermissions = PermissionSet.empty().grant(Permission.SEND_MESSAGES);
        SequencedStore store = new SequencedStore(guild(channelPermissions, rolePermissions, rolePermissions));
        GuildService service = new GuildService(store);
        assertThat(service.canSendMessages(GUILD, CHANNEL, MEMBER)).isTrue();
        assertThat(service.canManageMessages(GUILD, CHANNEL, MEMBER)).isTrue();
        assertThat(service.canAssignRole(GUILD, MEMBER, TARGET_ROLE)).isTrue();
        assertThat(service.canAssignRoles(GUILD, MEMBER, List.of(TARGET_ROLE))).isTrue();
        assertThat(store.reads).isEqualTo(4);
    }

    private static void assertChannelDecisionUsesOneSnapshot(Permission action) {
        Guild first = guild(PermissionSet.empty().grant(Permission.VIEW_CHANNEL), PermissionSet.empty(), PermissionSet.empty());
        Guild second = guild(PermissionSet.empty().grant(action), PermissionSet.empty(), PermissionSet.empty());
        SequencedStore store = new SequencedStore(first, second);
        GuildService service = new GuildService(store);
        boolean allowed = action == Permission.SEND_MESSAGES
            ? service.canSendMessages(GUILD, CHANNEL, MEMBER)
            : service.canManageMessages(GUILD, CHANNEL, MEMBER);
        assertThat(allowed).isFalse();
        assertThat(store.reads).isEqualTo(1);
    }

    private static SequencedStore roleStore() {
        Guild first = guild(PermissionSet.empty(), PermissionSet.empty(),
            PermissionSet.empty().grant(Permission.SEND_MESSAGES));
        Guild second = guild(PermissionSet.empty(), PermissionSet.empty().grant(Permission.SEND_MESSAGES),
            PermissionSet.empty().grant(Permission.MANAGE_CHANNELS));
        return new SequencedStore(first, second);
    }

    private static Guild guild(PermissionSet everyone, PermissionSet requester, PermissionSet target) {
        Map<UUID, Role> roles = new LinkedHashMap<>();
        roles.put(EVERYONE, new Role(EVERYONE, "@everyone", everyone));
        roles.put(REQUESTER_ROLE, new Role(REQUESTER_ROLE, "requester", requester));
        roles.put(TARGET_ROLE, new Role(TARGET_ROLE, "target", target));
        return new Guild(GUILD, "test", OWNER, EVERYONE,
            Map.of(MEMBER, new GuildMember(MEMBER, Set.of(EVERYONE, REQUESTER_ROLE))),
            roles, Map.of(CHANNEL, new Channel(CHANNEL, GUILD, "general", ChannelType.GUILD_TEXT, null, List.of())));
    }

    private static final class SequencedStore implements GuildStore {
        private final Guild[] snapshots;
        private int reads;

        private SequencedStore(Guild... snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        public Guild get(UUID guildId) {
            assertThat(guildId).isEqualTo(GUILD);
            return snapshots[Math.min(reads++, snapshots.length - 1)];
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
