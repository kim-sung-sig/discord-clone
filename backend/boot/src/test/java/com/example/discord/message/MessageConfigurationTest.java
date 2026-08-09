package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.discord.permission.AuthzProjectionUpdated;
import com.example.discord.permission.AuthorizationDecision;
import com.example.discord.permission.AuthorizationProjectionStore;
import com.example.discord.permission.AuthorizationResourceType;
import com.example.discord.permission.AuthorizationWatermarkAdvanced;
import com.example.discord.permission.Permission;
import com.example.discord.guild.InMemoryGuildService;
import java.util.UUID;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class MessageConfigurationTest {
    @Test
    void inMemoryMessageServiceIsRestrictedToTestProfile() throws Exception {
        Method factory = MessageConfiguration.class.getDeclaredMethod("inMemoryMessageService");

        assertThat(factory.getAnnotation(Profile.class).value()).containsExactly("test");
    }

    @Test
    void postgresMessagePublishGuardUsesLocalProjectionAndFailsClosed() {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuthorizationProjectionStore stale = new DecisionStore(AuthorizationDecision.deny(AuthorizationDecision.Reason.STALE_PROJECTION));
        MessagePublishGuard guard = new MessageConfiguration().projectedMessagePublishGuard(new InMemoryGuildService(), stale, true);

        assertThatThrownBy(() -> guard.requireCanPublish(new UserMessageAuthor(userId), new ChannelMessageTarget(guildId, channelId)))
            .hasMessageContaining("send messages permission required");

        MessagePublishGuard allowed = new MessageConfiguration().projectedMessagePublishGuard(
            new InMemoryGuildService(), new DecisionStore(AuthorizationDecision.allow()), true);
        allowed.requireCanPublish(new UserMessageAuthor(userId), new ChannelMessageTarget(guildId, channelId));
    }

    @Test
    void projectionFlagOffFallsBackToGuildAuthorization() {
        UUID ownerId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("fallback", ownerId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);

        MessagePublishGuard guard = new MessageConfiguration().projectedMessagePublishGuard(
            guilds, new DecisionStore(AuthorizationDecision.deny(AuthorizationDecision.Reason.STALE_PROJECTION)), false);

        guard.requireCanPublish(new UserMessageAuthor(ownerId), new ChannelMessageTarget(guild.id(), channel.id()));
    }

    private record DecisionStore(AuthorizationDecision decision) implements AuthorizationProjectionStore {
        public boolean apply(AuthzProjectionUpdated event) { return true; }
        public boolean advanceWatermark(AuthorizationWatermarkAdvanced event) { return true; }
        public AuthorizationDecision decide(UUID guildId, UUID subjectId, AuthorizationResourceType resourceType,
                                            UUID resourceId, Permission permission) { return decision; }
    }
}
