package com.example.discord.notification;

import com.example.discord.guild.GuildMember;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.Message;
import com.example.discord.message.SpecialMentionKind;
import com.example.discord.message.SpecialMentionTarget;
import com.example.discord.message.UserMentionTarget;
import com.example.discord.message.UserMessageAuthor;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MessageMentionRecipientResolver {
    private final InMemoryGuildService guilds;

    public MessageMentionRecipientResolver(InMemoryGuildService guilds) {
        this.guilds = Objects.requireNonNull(guilds, "guilds must not be null");
    }

    Set<UUID> resolve(Message message) {
        Objects.requireNonNull(message, "message must not be null");
        if (!(message.target() instanceof ChannelMessageTarget channel)) {
            return Set.of();
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        for (var mention : message.mentions()) {
            if (mention instanceof UserMentionTarget user) {
                recipients.add(user.userId());
            } else if (mention instanceof SpecialMentionTarget special && special.kind() == SpecialMentionKind.EVERYONE) {
                guilds.guild(channel.guildId()).members().stream().map(GuildMember::userId).forEach(recipients::add);
            }
        }
        if (message.author() instanceof UserMessageAuthor author) {
            recipients.remove(author.userId());
        }
        recipients.removeIf(userId -> !guilds.canViewChannel(channel.guildId(), channel.channelId(), userId));
        return Set.copyOf(recipients);
    }
}
