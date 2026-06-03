package com.example.discord.message;

public sealed interface MessageAuthor
    permits UserMessageAuthor, BotMessageAuthor, WebhookMessageAuthor, SystemMessageAuthor {
}
