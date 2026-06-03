package com.example.discord.message;

public sealed interface MessageTarget permits ChannelMessageTarget, DirectMessageTarget, ThreadMessageTarget {
}
