package com.example.discord.message;

import java.util.Objects;

public final class DefaultChannelMessageReader implements ChannelMessageReader {
    private final ChannelMessageReadGuard readGuard;
    private final ChannelMessagePagePort pages;

    public DefaultChannelMessageReader(
        ChannelMessageReadGuard readGuard,
        ChannelMessagePagePort pages
    ) {
        this.readGuard = Objects.requireNonNull(readGuard, "readGuard must not be null");
        this.pages = Objects.requireNonNull(pages, "pages must not be null");
    }

    @Override
    public MessagePage read(ChannelMessageQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        readGuard.requireCanRead(query);
        MessagePage page = pages.read(query.target(), query.beforeCursor(), query.limit());
        return new MessagePage(
            page.messages().stream().filter(message -> !message.deleted()).toList(),
            page.nextCursor()
        );
    }
}
