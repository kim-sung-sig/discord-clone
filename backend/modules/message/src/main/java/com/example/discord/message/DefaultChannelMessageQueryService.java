package com.example.discord.message;

import java.util.List;
import java.util.Objects;

public final class DefaultChannelMessageQueryService implements ChannelMessageQueryService {
    private static final int MAX_SEARCH_LIMIT = 50;

    private final ChannelMessageReadGuard readGuard;
    private final ChannelMessageReadModelPort readModels;

    public DefaultChannelMessageQueryService(
        ChannelMessageReadGuard readGuard,
        ChannelMessageReadModelPort readModels
    ) {
        this.readGuard = Objects.requireNonNull(readGuard, "readGuard must not be null");
        this.readModels = Objects.requireNonNull(readModels, "readModels must not be null");
    }

    @Override
    public MessageReadPage read(ChannelMessageQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        readGuard.requireCanRead(query);
        MessageReadPage page = readModels.readModels(query.target(), query.beforeCursor(), query.limit());
        return new MessageReadPage(
            page.messages().stream().filter(message -> !message.deleted()).toList(),
            page.nextCursor()
        );
    }

    @Override
    public List<MessageReadModel> search(ChannelMessageQuery query, String text, int limit) {
        Objects.requireNonNull(query, "query must not be null");
        if (text == null || text.isBlank()) {
            return List.of();
        }
        readGuard.requireCanRead(query);
        return readModels.searchModels(query.target(), text, Math.min(limit, MAX_SEARCH_LIMIT)).stream()
            .filter(message -> !message.deleted())
            .toList();
    }
}
