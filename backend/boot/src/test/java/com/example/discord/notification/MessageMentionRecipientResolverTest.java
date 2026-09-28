package com.example.discord.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.channel.ChannelType;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.message.BotMessageAuthor;
import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.Message;
import com.example.discord.message.MessageContent;
import com.example.discord.message.SpecialMentionKind;
import com.example.discord.message.SpecialMentionTarget;
import com.example.discord.message.UserMentionTarget;
import com.example.discord.message.UserMessageAuthor;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageMentionRecipientResolverTest {
    @Test
    void everyoneAndDirectMentionProduceOneNotificationPerVisibleRecipientExceptAuthor() {
        UUID authorId = UUID.randomUUID();
        UUID directId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("mentions", authorId);
        guilds.addMember(guild.id(), directId);
        guilds.addMember(guild.id(), otherId);
        var channel = guilds.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Message message = new Message(
            UUID.randomUUID(),
            new UserMessageAuthor(authorId),
            new ChannelMessageTarget(guild.id(), channel.id()),
            new MessageContent("<@" + directId + "> @everyone"),
            List.of(new UserMentionTarget(directId), new SpecialMentionTarget(SpecialMentionKind.EVERYONE)),
            false, false, List.of(), Instant.EPOCH, Instant.EPOCH
        );

        assertThat(new MessageMentionRecipientResolver(guilds).resolve(message))
            .isEqualTo(Set.of(directId, otherId));
    }

    @Test
    void directMentionCannotExposeHiddenChannelMember() {
        UUID ownerId = UUID.randomUUID();
        UUID hiddenId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("private", ownerId);
        guilds.addMember(guild.id(), hiddenId);
        var channel = guilds.createChannel(guild.id(), "private", ChannelType.GUILD_TEXT, null);
        guilds.addChannelRoleOverwrite(guild.id(), channel.id(), guild.everyoneRole().id(),
            PermissionSet.empty(), PermissionSet.empty().grant(Permission.VIEW_CHANNEL));
        Message message = new Message(UUID.randomUUID(), new UserMessageAuthor(ownerId),
            new ChannelMessageTarget(guild.id(), channel.id()), new MessageContent("hidden"),
            List.of(new UserMentionTarget(hiddenId)), false, false, List.of(), Instant.EPOCH, Instant.EPOCH);

        assertThat(new MessageMentionRecipientResolver(guilds).resolve(message)).isEmpty();
    }

    @Test
    void botEveryoneMentionNotifiesVisibleUsersWithoutAssumingUserAuthor() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("bots", ownerId);
        guilds.addMember(guild.id(), memberId);
        var channel = guilds.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Message message = new Message(UUID.randomUUID(), new BotMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(guild.id(), channel.id()), new MessageContent("@everyone"),
            List.of(new SpecialMentionTarget(SpecialMentionKind.EVERYONE)),
            false, false, List.of(), Instant.EPOCH, Instant.EPOCH);

        assertThat(new MessageMentionRecipientResolver(guilds).resolve(message))
            .isEqualTo(Set.of(ownerId, memberId));
    }
}
