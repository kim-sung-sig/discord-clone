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
import com.example.discord.gateway.InMemoryGatewayService;
import com.example.discord.notification.MessageMentionProjector;
import com.example.discord.notification.MessageMentionRecipientResolver;
import com.example.discord.notification.MentionNotificationCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.ObjectProvider;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageConfigurationTest {
    @Test
    void inMemoryMessageServiceIsRestrictedToTestProfile() throws Exception {
        Method factory = MessageConfiguration.class.getDeclaredMethod("inMemoryMessageService");

        assertThat(factory.getAnnotation(Profile.class).value()).containsExactly("test");
    }

    @Test
    void inMemoryPublicationFallbacksAreRestrictedToTestProfile() throws Exception {
        Method outbox = MessageConfiguration.class.getDeclaredMethod("messagePublicationOutbox");
        Method inbox = MessageKafkaConfiguration.class.getDeclaredMethod("messagePublicationInbox");

        assertThat(outbox.getAnnotation(Profile.class).value()).containsExactly("test & !postgres");
        assertThat(inbox.getAnnotation(Profile.class).value()).containsExactly("test & !postgres");
    }

    @Test
    void postgresMessagePublishGuardUsesLocalProjectionAndFailsClosed() {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuthorizationProjectionStore stale = new DecisionStore(AuthorizationDecision.deny(AuthorizationDecision.Reason.STALE_PROJECTION));
        MessagePublishGuard guard = new MessageConfiguration().projectedMessageAuthorizationPolicy(new InMemoryGuildService(), stale, true);

        assertThatThrownBy(() -> guard.requireCanPublish(new UserMessageAuthor(userId), new ChannelMessageTarget(guildId, channelId)))
            .hasMessageContaining("send messages permission required");

        MessagePublishGuard allowed = new MessageConfiguration().projectedMessageAuthorizationPolicy(
            new InMemoryGuildService(), new DecisionStore(AuthorizationDecision.allow()), true);
        allowed.requireCanPublish(new UserMessageAuthor(userId), new ChannelMessageTarget(guildId, channelId));
    }

    @Test
    void projectionFlagOffFallsBackToGuildAuthorization() {
        UUID ownerId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("fallback", ownerId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);

        MessagePublishGuard guard = new MessageConfiguration().projectedMessageAuthorizationPolicy(
            guilds, new DecisionStore(AuthorizationDecision.deny(AuthorizationDecision.Reason.STALE_PROJECTION)), false);

        guard.requireCanPublish(new UserMessageAuthor(ownerId), new ChannelMessageTarget(guild.id(), channel.id()));
    }

    @Test
    void messagePublishedDispatcherPreservesSourceEventId() {
        UUID ownerId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("dispatcher", ownerId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);
        InMemoryGatewayService gateway = new InMemoryGatewayService(
            guilds,
            Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofSeconds(30)
        );
        var session = gateway.identify(ownerId).session();
        UUID messageId = UUID.randomUUID();
        Message message = new Message(
            messageId,
            new UserMessageAuthor(ownerId),
            new ChannelMessageTarget(guild.id(), channel.id()),
            new MessageContent("hello"),
            List.of(),
            false,
            false,
            List.of(),
            Instant.parse("2026-08-12T00:00:00Z"),
            Instant.parse("2026-08-12T00:00:00Z")
        );
        UUID sourceEventId = UUID.randomUUID();
        MessagePublished published = new MessagePublished(
            sourceEventId,
            messageId,
            message.author(),
            (ChannelMessageTarget) message.target(),
            List.of(),
            "correlation",
            message.createdAt()
        );

        new MessageConfiguration().messagePublishedDispatcher(
            gateway,
            (target, id) -> message,
            mock(ObjectProvider.class)
        ).dispatch(published);

        assertThat(gateway.poll(session.id(), ownerId, 0).stream()
            .map(com.example.discord.gateway.GatewayEvent::busEventId))
            .contains(sourceEventId.toString());
    }

    @Test
    void localDispatcherProjectsPublishedMention() {
        UUID authorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("mentions", authorId);
        guilds.addMember(guild.id(), recipientId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);
        InMemoryGatewayService gateway = new InMemoryGatewayService(guilds, Clock.systemUTC(), Duration.ofSeconds(30));
        Message message = new Message(UUID.randomUUID(), new UserMessageAuthor(authorId),
            new ChannelMessageTarget(guild.id(), channel.id()), new MessageContent("<@" + recipientId + ">"),
            List.of(new UserMentionTarget(recipientId)), false, false, List.of(), Instant.EPOCH, Instant.EPOCH);
        MessagePublished event = new MessagePublished(UUID.randomUUID(), message.id(), message.author(), message.target(),
            message.mentions(), "correlation", Instant.EPOCH);
        List<MentionNotificationCommand> recorded = new ArrayList<>();
        MessageMentionProjector projector = new MessageMentionProjector((target, id) -> message,
            new MessageMentionRecipientResolver(guilds), id -> 7L, recorded::add);
        ObjectProvider<MessageMentionProjector> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(projector);

        new MessageConfiguration().messagePublishedDispatcher(gateway, (target, id) -> message, provider).dispatch(event);

        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().mentionedUserIds()).containsExactly(recipientId);
    }

    private record DecisionStore(AuthorizationDecision decision) implements AuthorizationProjectionStore {
        public boolean apply(AuthzProjectionUpdated event) { return true; }
        public boolean advanceWatermark(AuthorizationWatermarkAdvanced event) { return true; }
        public AuthorizationDecision decide(UUID guildId, UUID subjectId, AuthorizationResourceType resourceType,
                                            UUID resourceId, Permission permission) { return decision; }
    }
}
