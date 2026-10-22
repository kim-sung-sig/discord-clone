package com.example.discord.notification;

import com.example.discord.guild.Guild;
import com.example.discord.guild.GuildMember;
import com.example.discord.guild.GuildService;
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
    private final GuildService guilds;

    public MessageMentionRecipientResolver(GuildService guilds) {
        this.guilds = Objects.requireNonNull(guilds, "guilds must not be null");
    }

    Set<UUID> resolve(Message message) {
        Objects.requireNonNull(message, "message must not be null");
        if (!(message.target() instanceof ChannelMessageTarget channel)) {
            return Set.of();
        }
        UUID authorId = message.author() instanceof UserMessageAuthor author ? author.userId() : null;
        Set<UUID> recipients = new LinkedHashSet<>();
        boolean everyone = false;
        for (var mention : message.mentions()) {
            if (mention instanceof UserMentionTarget user) {
                recipients.add(user.userId());
            } else if (mention instanceof SpecialMentionTarget special && special.kind() == SpecialMentionKind.EVERYONE) {
                everyone = true;
            }
        }
        recipients.remove(authorId);
        if (!everyone && recipients.isEmpty()) {
            return Set.of();
        }
        Guild guild = guilds.guild(channel.guildId());
        if (everyone) {
            guild.members().stream().map(GuildMember::userId).forEach(recipients::add);
            recipients.remove(authorId);
        }
        return Set.copyOf(guilds.channelViewers(guild, channel.channelId(), recipients));
    }
}
