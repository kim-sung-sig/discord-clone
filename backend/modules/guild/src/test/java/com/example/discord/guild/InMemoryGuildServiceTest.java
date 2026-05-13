package com.example.discord.guild;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryGuildServiceTest {
    @Test
    void createsGuildWithOwnerMemberAndEveryoneRole() {
        InMemoryGuildService service = new InMemoryGuildService();
        UUID ownerId = UUID.randomUUID();

        Guild guild = service.createGuild("Discord Clone", ownerId);

        assertThat(guild.name()).isEqualTo("Discord Clone");
        assertThat(guild.ownerId()).isEqualTo(ownerId);
        assertThat(guild.members()).extracting(GuildMember::userId).containsExactly(ownerId);
        assertThat(guild.everyoneRole().name()).isEqualTo("@everyone");
        assertThat(guild.member(ownerId).roleIds()).contains(guild.everyoneRole().id());
    }

    @Test
    void filtersVisibleChannelsUsingEffectiveViewChannelPermission() {
        InMemoryGuildService service = new InMemoryGuildService();
        UUID ownerId = UUID.randomUUID();
        Guild guild = service.createGuild("Discord Clone", ownerId);
        Channel general = service.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Channel staff = service.createChannel(guild.id(), "staff", ChannelType.GUILD_TEXT, null);

        service.assignRolePermissions(guild.id(), guild.everyoneRole().id(), PermissionSet.empty().grant(Permission.VIEW_CHANNEL));
        service.addChannelRoleOverwrite(
            guild.id(),
            staff.id(),
            guild.everyoneRole().id(),
            PermissionSet.empty(),
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
        );

        assertThat(service.visibleChannels(guild.id(), ownerId)).extracting(Channel::id)
            .containsExactly(general.id());
    }

    @Test
    void administratorRoleSeesChannelEvenWhenEveryoneDenied() {
        InMemoryGuildService service = new InMemoryGuildService();
        UUID ownerId = UUID.randomUUID();
        Guild guild = service.createGuild("Discord Clone", ownerId);
        Channel adminOnly = service.createChannel(guild.id(), "admin-only", ChannelType.GUILD_TEXT, null);
        Role adminRole = service.createRole(guild.id(), "admin");

        service.assignRolePermissions(guild.id(), adminRole.id(), PermissionSet.empty().grant(Permission.ADMINISTRATOR));
        service.assignRoleToMember(guild.id(), ownerId, adminRole.id());
        service.addChannelRoleOverwrite(
            guild.id(),
            adminOnly.id(),
            guild.everyoneRole().id(),
            PermissionSet.empty(),
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
        );

        assertThat(service.visibleChannels(guild.id(), ownerId)).extracting(Channel::id)
            .containsExactly(adminOnly.id());
    }
}
