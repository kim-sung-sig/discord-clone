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
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
            userIds -> Map.of(ownerId, "Owner Display"),
            mock(ObjectProvider.class)
        ).dispatch(published);

        List<com.example.discord.gateway.GatewayEvent> events = gateway.poll(session.id(), ownerId, 0);
        assertThat(events.stream().map(com.example.discord.gateway.GatewayEvent::busEventId))
            .contains(sourceEventId.toString());
        assertThat(events.stream().filter(event -> sourceEventId.toString().equals(event.busEventId())).findFirst())
            .get()
            .satisfies(event -> {
                assertThat(event.payload()).containsEntry("authorId", ownerId.toString());
                assertThat(event.payload()).containsEntry("authorDisplayName", "Owner Display");
            });
    }

    @Test
    void localDispatcherOmitsAuthorDisplayNameWhenTheAuthorNoLongerExists() {
        UUID ownerId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("dispatcher-unknown-author", ownerId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);
        InMemoryGatewayService gateway = new InMemoryGatewayService(
            guilds,
            Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofSeconds(30)
        );
        var session = gateway.identify(ownerId).session();
        Message message = new Message(UUID.randomUUID(), new UserMessageAuthor(ownerId),
            new ChannelMessageTarget(guild.id(), channel.id()), new MessageContent("hello"),
            List.of(), false, false, List.of(), Instant.EPOCH, Instant.EPOCH);
        UUID sourceEventId = UUID.randomUUID();
        MessagePublished published = new MessagePublished(sourceEventId, message.id(), message.author(), message.target(),
            List.of(), "correlation", Instant.EPOCH);

        new MessageConfiguration().messagePublishedDispatcher(
            gateway,
            (target, id) -> message,
            userIds -> Map.of(),
            mock(ObjectProvider.class)
        ).dispatch(published);

        assertThat(gateway.poll(session.id(), ownerId, 0).stream()
            .filter(event -> sourceEventId.toString().equals(event.busEventId()))
            .findFirst())
            .get()
            .satisfies(event -> {
                assertThat(event.payload()).containsEntry("authorId", ownerId.toString());
                assertThat(event.payload()).doesNotContainKey("authorDisplayName");
            });
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

        new MessageConfiguration().messagePublishedDispatcher(gateway, (target, id) -> message, userIds -> Map.of(), provider)
            .dispatch(event);

        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().mentionedUserIds()).containsExactly(recipientId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void localDispatcherPreservesMessageCreatePayloadShape(boolean hasAuthorDisplayName) {
        UUID ownerId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("dispatcher", ownerId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);
        InMemoryGatewayService gateway = new InMemoryGatewayService(
            guilds, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), Duration.ofSeconds(30)
        );
        var session = gateway.identify(ownerId).session();
        UUID mentionedUserId = UUID.randomUUID();
        UUID mentionedRoleId = UUID.randomUUID();
        List<MessageMentionTarget> mentions = List.of(
            new UserMentionTarget(mentionedUserId),
            new RoleMentionTarget(mentionedRoleId),
            new ChannelMentionTarget(channel.id()),
            new SpecialMentionTarget(SpecialMentionKind.HERE)
        );
        Message message = new Message(
            UUID.randomUUID(), new UserMessageAuthor(ownerId), new ChannelMessageTarget(guild.id(), channel.id()),
            new MessageContent("hello"), mentions, false, false, List.of(), Instant.EPOCH, Instant.EPOCH
        );
        UUID eventId = UUID.randomUUID();
        MessagePublished published = new MessagePublished(
            eventId, message.id(), message.author(), message.target(), message.mentions(), "correlation", message.createdAt()
        );

        new MessageConfiguration().messagePublishedDispatcher(
            gateway,
            (target, id) -> message,
            userIds -> hasAuthorDisplayName ? Map.of(ownerId, "Owner Display") : Map.of(),
            mock(ObjectProvider.class)
        ).dispatch(published);

        Map<String, Object> gatewayPayload = gateway.poll(session.id(), ownerId, 0).stream()
            .filter(event -> eventId.toString().equals(event.busEventId()))
            .findFirst()
            .orElseThrow()
            .payload();
        List<String> expectedKeys = new ArrayList<>(List.of(
            "id", "guildId", "channelId", "authorId", "content", "mentions",
            "pinned", "deleted", "edited", "createdAt", "updatedAt"
        ));
        if (hasAuthorDisplayName) {
            expectedKeys.add("authorDisplayName");
        }
        List<String> expectedTokens = List.of(
            mentionedUserId.toString(), mentionedRoleId.toString(), channel.id().toString(), "here"
        );

        assertThat(gatewayPayload).containsOnlyKeys(expectedKeys.toArray(String[]::new));
        assertThat(gatewayPayload).containsEntry("mentions", expectedTokens);
    }

    private record DecisionStore(AuthorizationDecision decision) implements AuthorizationProjectionStore {
        public boolean apply(AuthzProjectionUpdated event) { return true; }
        public boolean advanceWatermark(AuthorizationWatermarkAdvanced event) { return true; }
        public AuthorizationDecision decide(UUID guildId, UUID subjectId, AuthorizationResourceType resourceType,
                                            UUID resourceId, Permission permission) { return decision; }
    }
}
