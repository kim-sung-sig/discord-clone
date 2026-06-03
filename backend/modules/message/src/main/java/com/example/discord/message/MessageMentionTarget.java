package com.example.discord.message;

public sealed interface MessageMentionTarget
    permits UserMentionTarget, RoleMentionTarget, ChannelMentionTarget, SpecialMentionTarget {
}
