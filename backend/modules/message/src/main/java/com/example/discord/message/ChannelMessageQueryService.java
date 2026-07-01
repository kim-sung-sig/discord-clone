package com.example.discord.message;

import java.util.List;

public interface ChannelMessageQueryService {
    MessageReadPage read(ChannelMessageQuery query);

    List<MessageReadModel> search(ChannelMessageQuery query, String text, int limit);
}
