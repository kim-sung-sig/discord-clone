package com.example.discord.notification;

import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.Message;
import com.example.discord.message.MessageLookupPort;
import com.example.discord.message.UserMessageAuthor;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

public final class MessageMentionProjector {
    private final MessageLookupPort messages;
    private final MessageMentionRecipientResolver recipients;
    private final ToLongFunction<UUID> sequences;
    private final Consumer<MentionNotificationCommand> writer;

    public MessageMentionProjector(
        MessageLookupPort messages,
        MessageMentionRecipientResolver recipients,
        ToLongFunction<UUID> sequences,
        Consumer<MentionNotificationCommand> writer
    ) {
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.recipients = Objects.requireNonNull(recipients, "recipients must not be null");
        this.sequences = Objects.requireNonNull(sequences, "sequences must not be null");
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
    }

    public void project(ChannelMessageTarget target, UUID messageId) {
        Message message = messages.requireMessage(target, messageId);
        var userIds = recipients.resolve(message);
        if (userIds.isEmpty()) {
            return;
        }
        writer.accept(new MentionNotificationCommand(
            target.guildId(), target.channelId(), messageId, sequences.applyAsLong(messageId),
            message.author() instanceof UserMessageAuthor author ? author.userId() : null,
            userIds, userIds, "Mention in channel"
        ));
    }
}
