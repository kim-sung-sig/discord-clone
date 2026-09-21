package com.example.discord.message;

import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.permission.AuthorizationProjectionStore;
import com.example.discord.permission.AuthorizationResourceType;
import com.example.discord.permission.Permission;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class MessageAuthorizationPolicy implements
    MessagePublishGuard,
    ChannelMessageReadGuard,
    MessageMutationGuard {
    private final InMemoryGuildService guilds;
    private final AuthorizationProjectionStore projections;
    private final boolean projectionEnabled;

    MessageAuthorizationPolicy(InMemoryGuildService guilds) {
        this(guilds, null, false);
    }

    MessageAuthorizationPolicy(
        InMemoryGuildService guilds,
        AuthorizationProjectionStore projections,
        boolean projectionEnabled
    ) {
        this.guilds = Objects.requireNonNull(guilds, "guilds must not be null");
        this.projections = projections;
        this.projectionEnabled = projectionEnabled;
        if (projectionEnabled) {
            Objects.requireNonNull(projections, "projections must not be null when enabled");
        }
    }

    @Override
    public void requireCanPublish(MessageAuthor author, MessageTarget target) {
        if (author instanceof UserMessageAuthor user && target instanceof ChannelMessageTarget channel) {
            boolean allowed = projectionEnabled
                ? projected(channel, user, Permission.VIEW_CHANNEL)
                    && projected(channel, user, Permission.SEND_MESSAGES)
                : guilds.canSendMessages(channel.guildId(), channel.channelId(), user.userId());
            if (!allowed) {
                throw forbidden("send messages permission required");
            }
            return;
        }
        throw forbidden("unsupported message author or target");
    }

    @Override
    public void requireCanRead(ChannelMessageQuery query) {
        if (query.requester() instanceof UserMessageAuthor user) {
            ChannelMessageTarget channel = query.target();
            boolean allowed = projectionEnabled
                ? projected(channel, user, Permission.VIEW_CHANNEL)
                : guilds.canViewChannel(channel.guildId(), channel.channelId(), user.userId());
            if (!allowed) {
                throw forbidden("view channel permission required");
            }
            return;
        }
        throw forbidden("unsupported message reader");
    }

    @Override
    public void requireCanEdit(MessageAuthor actor, Message message) {
        UserMessageAuthor user = requireUser(actor);
        ChannelMessageTarget channel = requireChannel(message);
        if (!message.authorId().equals(user.userId())
            || message.deleted()
            || !guilds.canSendMessages(channel.guildId(), channel.channelId(), user.userId())) {
            throw forbidden("message author required");
        }
    }

    @Override
    public void requireCanDelete(MessageAuthor actor, Message message) {
        UserMessageAuthor user = requireUser(actor);
        ChannelMessageTarget channel = requireChannel(message);
        boolean author = message.authorId().equals(user.userId())
            && !message.deleted()
            && guilds.canViewChannel(channel.guildId(), channel.channelId(), user.userId());
        if (!author && !guilds.canManageMessages(channel.guildId(), channel.channelId(), user.userId())) {
            throw forbidden("manage messages permission required");
        }
    }

    @Override
    public void requireCanPin(MessageAuthor actor, Message message) {
        UserMessageAuthor user = requireUser(actor);
        ChannelMessageTarget channel = requireChannel(message);
        if (!guilds.canManageMessages(channel.guildId(), channel.channelId(), user.userId())) {
            throw forbidden("manage messages permission required");
        }
    }

    private boolean projected(ChannelMessageTarget channel, UserMessageAuthor user, Permission permission) {
        return projections.decide(
            channel.guildId(),
            user.userId(),
            AuthorizationResourceType.CHANNEL,
            channel.channelId(),
            permission
        ).allowed();
    }

    private static UserMessageAuthor requireUser(MessageAuthor actor) {
        if (actor instanceof UserMessageAuthor user) {
            return user;
        }
        throw forbidden("unsupported message actor");
    }

    private static ChannelMessageTarget requireChannel(Message message) {
        if (message.target() instanceof ChannelMessageTarget channel) {
            return channel;
        }
        throw forbidden("unsupported message target");
    }

    private static ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }
}
