package com.example.discord.message;

public interface MessageMutationGuard {
    void requireCanEdit(MessageAuthor actor, Message message);

    void requireCanDelete(MessageAuthor actor, Message message);

    void requireCanPin(MessageAuthor actor, Message message);
}
