package com.example.discord.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.discord.channel.ChannelType;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.Message;
import com.example.discord.message.MessageContent;
import com.example.discord.message.MessagePublished;
import com.example.discord.message.MessagePublishedRecord;
import com.example.discord.message.UserMentionTarget;
import com.example.discord.message.UserMessageAuthor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

class KafkaMessageMentionConsumerTest {
    @Test
    void bodyFreePublicationReachesNotificationProjectorInItsOwnConsumerGroup() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("guild", authorId);
        guilds.addMember(guild.id(), recipientId);
        var channel = guilds.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Message message = new Message(UUID.randomUUID(), new UserMessageAuthor(authorId),
            new ChannelMessageTarget(guild.id(), channel.id()), new MessageContent("private body"),
            List.of(new UserMentionTarget(recipientId)), false, false, List.of(), Instant.EPOCH, Instant.EPOCH);
        MessagePublished event = new MessagePublished(UUID.randomUUID(), message.id(), message.author(), message.target(),
            message.mentions(), "correlation", Instant.EPOCH);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        List<MentionNotificationCommand> recorded = new ArrayList<>();
        var projector = new MessageMentionProjector((target, id) -> message,
            new MessageMentionRecipientResolver(guilds), id -> 12L, recorded::add);

        new KafkaMessageMentionConsumer(mapper, projector).consume(
            mapper.writeValueAsString(MessagePublishedRecord.from(event, "payload-hash")));

        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().mentionedUserIds()).containsExactly(recipientId);
        assertThat(KafkaMessageMentionConsumer.class.getDeclaredMethod("consume", String.class)
            .getAnnotation(KafkaListener.class).groupId()).isEqualTo("notification-service-v1");
    }

    @Test
    void projectionFailureIsNotReportedAsMalformedKafkaRecord() throws Exception {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var projector = new MessageMentionProjector(
            (target, id) -> { throw new IllegalArgumentException("message lookup failed"); },
            new MessageMentionRecipientResolver(new InMemoryGuildService()), id -> 1L, command -> {});
        String payload = mapper.writeValueAsString(new MessagePublishedRecord(
            1, UUID.randomUUID(), messageId, guildId, channelId, "correlation", Instant.EPOCH,
            messageId, "payload-hash"));

        assertThatThrownBy(() -> new KafkaMessageMentionConsumer(mapper, projector).consume(payload))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("message lookup failed");
    }
}
