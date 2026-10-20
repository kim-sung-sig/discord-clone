package com.example.discord.notification;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.discord.channel.ChannelType;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.Message;
import com.example.discord.message.MessageContent;
import com.example.discord.message.UserMentionTarget;
import com.example.discord.message.UserMessageAuthor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationProjectionConfigurationTest {
    @Test
    void postgresProjectorReadsSavedSequenceAndWritesMention() {
        UUID authorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("guild", authorId);
        guilds.addMember(guild.id(), recipientId);
        var channel = guilds.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Message message = new Message(UUID.randomUUID(), new UserMessageAuthor(authorId),
            new ChannelMessageTarget(guild.id(), channel.id()), new MessageContent("mention"),
            List.of(new UserMentionTarget(recipientId)), false, false, List.of(), Instant.EPOCH, Instant.EPOCH);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcNotificationInboxService inbox = mock(JdbcNotificationInboxService.class);
        when(jdbc.queryForObject("SELECT sequence FROM messages WHERE id = ?", Long.class, message.id()))
            .thenReturn(42L);

        var projector = new NotificationProjectionConfiguration().messageMentionProjector(
            (target, id) -> message, guilds, jdbc, inbox);
        projector.project((ChannelMessageTarget) message.target(), message.id());

        verify(inbox).recordMention(argThat(command -> command.sequence() == 42L
            && command.mentionedUserIds().contains(recipientId)));
    }
}
