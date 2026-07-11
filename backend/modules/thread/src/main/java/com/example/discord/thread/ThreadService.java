package com.example.discord.thread;

import java.util.UUID;

public interface ThreadService {
    ThreadChannel createThread(CreateThreadCommand command);
    ForumTag createForumTag(UUID guildId, UUID forumChannelId, String name);
    ForumPost createForumPost(CreateForumPostCommand command);
    ThreadWriteReceipt write(ThreadWriteCommand command);
    ThreadChannel archive(UUID guildId, UUID threadId);
    ThreadChannel reopen(UUID guildId, UUID threadId);
    ThreadChannel thread(UUID guildId, UUID threadId);
    int archiveExpired();
}
