package com.example.discord.message;

import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.moderation.InMemoryModerationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Configuration
class MessageConfiguration {
    @Bean
    @Profile("!postgres & !production & !admin-cli")
    InMemoryMessageService inMemoryMessageService() {
        return new InMemoryMessageService();
    }

    @Bean
    PublishMessageUseCase publishMessageUseCase(
        MessagePublicationStore publications,
        MessagePublishGuard publishGuard,
        MessageContentPolicy contentPolicy
    ) {
        return new DefaultPublishMessageUseCase(publishGuard, contentPolicy, publications, Clock.systemUTC());
    }

    @Bean
    EditMessageUseCase editMessageUseCase(
        MessageMutationGuard mutationGuard,
        MessageContentPolicy contentPolicy,
        MessageStore messages
    ) {
        return new DefaultEditMessageUseCase(mutationGuard, contentPolicy, messages, Clock.systemUTC());
    }

    @Bean
    DeleteMessageUseCase deleteMessageUseCase(
        MessageMutationGuard mutationGuard,
        MessageStore messages
    ) {
        return new DefaultDeleteMessageUseCase(mutationGuard, messages, Clock.systemUTC());
    }

    @Bean
    PinMessageUseCase pinMessageUseCase(
        MessageMutationGuard mutationGuard,
        MessageStore messages
    ) {
        return new DefaultPinMessageUseCase(mutationGuard, messages, Clock.systemUTC());
    }

    @Bean
    ChannelMessageReader channelMessageReader(
        ChannelMessageReadGuard readGuard,
        ChannelMessagePagePort pages
    ) {
        return new DefaultChannelMessageReader(readGuard, pages);
    }

    @Bean
    MessagePublishGuard messagePublishGuard(InMemoryGuildService guildService) {
        return (author, target) -> {
            if (author instanceof UserMessageAuthor user && target instanceof ChannelMessageTarget channel) {
                if (!guildService.canSendMessages(channel.guildId(), channel.channelId(), user.userId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "send messages permission required");
                }
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "unsupported message author or target");
        };
    }

    @Bean
    ChannelMessageReadGuard channelMessageReadGuard(InMemoryGuildService guildService) {
        return query -> {
            if (query.requester() instanceof UserMessageAuthor user) {
                ChannelMessageTarget channel = query.target();
                if (!guildService.canViewChannel(channel.guildId(), channel.channelId(), user.userId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
                }
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "unsupported message reader");
        };
    }

    @Bean
    MessageMutationGuard messageMutationGuard(InMemoryGuildService guildService) {
        return new MessageMutationGuard() {
            @Override
            public void requireCanEdit(MessageAuthor actor, Message message) {
                UserMessageAuthor user = requireUserActor(actor);
                ChannelMessageTarget channel = requireChannelTarget(message);
                if (
                    !message.authorId().equals(user.userId())
                        || message.deleted()
                        || !guildService.canSendMessages(channel.guildId(), channel.channelId(), user.userId())
                ) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "message author required");
                }
            }

            @Override
            public void requireCanDelete(MessageAuthor actor, Message message) {
                UserMessageAuthor user = requireUserActor(actor);
                ChannelMessageTarget channel = requireChannelTarget(message);
                boolean author = message.authorId().equals(user.userId())
                    && !message.deleted()
                    && guildService.canViewChannel(channel.guildId(), channel.channelId(), user.userId());
                if (!author && !guildService.canManageMessages(channel.guildId(), channel.channelId(), user.userId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage messages permission required");
                }
            }

            @Override
            public void requireCanPin(MessageAuthor actor, Message message) {
                UserMessageAuthor user = requireUserActor(actor);
                ChannelMessageTarget channel = requireChannelTarget(message);
                if (!guildService.canManageMessages(channel.guildId(), channel.channelId(), user.userId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage messages permission required");
                }
            }
        };
    }

    @Bean
    MessageContentPolicy messageContentPolicy(InMemoryModerationService moderationService) {
        return (author, target, content, mentions) -> {
            if (author instanceof UserMessageAuthor user && target instanceof ChannelMessageTarget channel) {
                var decision = moderationService.evaluateMessage(
                    channel.guildId(),
                    channel.channelId(),
                    user.userId(),
                    content.value()
                );
                if (decision.blocked()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, decision.reason());
                }
            }
        };
    }

    @Bean
    @Profile("!postgres")
    MessagePublicationOutbox messagePublicationOutbox() {
        return event -> {
        };
    }

    private static UserMessageAuthor requireUserActor(MessageAuthor actor) {
        if (actor instanceof UserMessageAuthor user) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "unsupported message actor");
    }

    private static ChannelMessageTarget requireChannelTarget(Message message) {
        if (message.target() instanceof ChannelMessageTarget channel) {
            return channel;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "unsupported message target");
    }
}
