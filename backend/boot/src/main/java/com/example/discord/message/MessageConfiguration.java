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
    @Profile("postgres")
    InMemoryMessageService persistentMessageService(MessageSnapshotStore snapshots) {
        return new PersistentMessageService(snapshots);
    }

    @Bean
    PublishMessageUseCase publishMessageUseCase(
        MessageStore messages,
        MessagePublishGuard publishGuard,
        MessageContentPolicy contentPolicy,
        MessagePublicationOutbox outbox
    ) {
        return new DefaultPublishMessageUseCase(publishGuard, contentPolicy, messages, outbox, Clock.systemUTC());
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
    MessagePublicationOutbox messagePublicationOutbox() {
        return event -> {
        };
    }
}
