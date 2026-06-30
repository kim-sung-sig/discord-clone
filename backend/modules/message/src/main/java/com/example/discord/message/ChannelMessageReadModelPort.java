package com.example.discord.message;

import java.util.List;

public interface ChannelMessageReadModelPort {
    MessageReadPage readModels(ChannelMessageTarget target, String beforeCursor, int limit);

    List<MessageReadModel> searchModels(ChannelMessageTarget target, String query, int limit);
}
