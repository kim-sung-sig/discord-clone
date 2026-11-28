package com.example.discord.message;

import java.util.List;
import java.util.Locale;

public final class MessageMentionTokens {
    private MessageMentionTokens() {
    }

    public static String token(MessageMentionTarget mention) {
        return switch (mention) {
            case UserMentionTarget user -> user.userId().toString();
            case RoleMentionTarget role -> role.roleId().toString();
            case ChannelMentionTarget channel -> channel.channelId().toString();
            case SpecialMentionTarget special -> special.kind().name().toLowerCase(Locale.ROOT);
        };
    }

    public static List<String> tokens(List<MessageMentionTarget> mentions) {
        return mentions.stream().map(MessageMentionTokens::token).toList();
    }
}
