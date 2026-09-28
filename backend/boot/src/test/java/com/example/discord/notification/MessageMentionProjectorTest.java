package com.example.discord.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.channel.ChannelType;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.message.BotMessageAuthor;
import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.Message;
import com.example.discord.message.MessageContent;
import com.example.discord.message.UserMentionTarget;
import com.example.discord.message.UserMessageAuthor;
import com.example.discord.message.SpecialMentionKind;
import com.example.discord.message.SpecialMentionTarget;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MessageMentionProjectorTest {
    @Test
    void persistedMessageBecomesOneVisibleMentionCommandWithDatabaseSequence() {
        UUID authorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("mentions", authorId);
        guilds.addMember(guild.id(), recipientId);
        var channel = guilds.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Message message = new Message(UUID.randomUUID(), new UserMessageAuthor(authorId),
            new ChannelMessageTarget(guild.id(), channel.id()), new MessageContent("secret body"),
            List.of(new UserMentionTarget(recipientId)), false, false, List.of(), Instant.EPOCH, Instant.EPOCH);
        List<MentionNotificationCommand> recorded = new ArrayList<>();
        MessageMentionProjector projector = new MessageMentionProjector(
            (target, id) -> message,
            new MessageMentionRecipientResolver(guilds),
            id -> 42L,
            recorded::add
        );

        projector.project((ChannelMessageTarget) message.target(), message.id());

        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().mentionedUserIds()).isEqualTo(Set.of(recipientId));
        assertThat(recorded.getFirst().visibleRecipientIds()).isEqualTo(Set.of(recipientId));
        assertThat(recorded.getFirst().sequence()).isEqualTo(42L);
        assertThat(recorded.getFirst().summary()).doesNotContain("secret body");
    }

    @Test
    void botMentionCanBeRecordedWithoutPretendingBotIsAUser() {
        UUID recipientId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("bots", UUID.randomUUID());
        guilds.addMember(guild.id(), recipientId);
        var channel = guilds.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Message message = new Message(UUID.randomUUID(), new BotMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(guild.id(), channel.id()), new MessageContent("bot mention"),
            List.of(new UserMentionTarget(recipientId)), false, false, List.of(), Instant.EPOCH, Instant.EPOCH);
        List<MentionNotificationCommand> recorded = new ArrayList<>();
        MessageMentionProjector projector = new MessageMentionProjector(
            (target, id) -> message, new MessageMentionRecipientResolver(guilds), id -> 8L, recorded::add);

        projector.project((ChannelMessageTarget) message.target(), message.id());

        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().authorId()).isNull();
    }

    @Test
    void hereMentionDoesNotReadSequenceOrWriteNotification() {
        UUID authorId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("guild", authorId);
        var channel = guilds.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Message message = new Message(UUID.randomUUID(), new UserMessageAuthor(authorId),
            new ChannelMessageTarget(guild.id(), channel.id()), new MessageContent("@here"),
            List.of(new SpecialMentionTarget(SpecialMentionKind.HERE)),
            false, false, List.of(), Instant.EPOCH, Instant.EPOCH);
        AtomicInteger sequenceReads = new AtomicInteger();
        List<MentionNotificationCommand> recorded = new ArrayList<>();
        var projector = new MessageMentionProjector((target, id) -> message,
            new MessageMentionRecipientResolver(guilds), id -> { sequenceReads.incrementAndGet(); return 1L; }, recorded::add);

        projector.project((ChannelMessageTarget) message.target(), message.id());

        assertThat(sequenceReads).hasValue(0);
        assertThat(recorded).isEmpty();
    }
}
