package com.example.discord.message;

import com.example.discord.gateway.InMemoryGatewayService;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.moderation.InMemoryModerationService;
import com.example.discord.permission.AuthorizationProjectionStore;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class MessageConfiguration {
    @Bean
    @Profile("test")
    InMemoryMessageService inMemoryMessageService() {
        return new InMemoryMessageService();
    }

    @Bean
    PublishMessageUseCase publishMessageUseCase(
        MessagePublicationStore publications,
        MessagePublishGuard publishGuard,
        MessageContentPolicy contentPolicy
    ) {
        return new PublishMessageUseCase(publishGuard, contentPolicy, publications, Clock.systemUTC());
    }

    @Bean
    EditMessageUseCase editMessageUseCase(
        MessageMutationGuard mutationGuard,
        MessageContentPolicy contentPolicy,
        MessageStore messages
    ) {
        return new EditMessageUseCase(mutationGuard, contentPolicy, messages, Clock.systemUTC());
    }

    @Bean
    DeleteMessageUseCase deleteMessageUseCase(
        MessageMutationGuard mutationGuard,
        MessageStore messages
    ) {
        return new DeleteMessageUseCase(mutationGuard, messages, Clock.systemUTC());
    }

    @Bean
    PinMessageUseCase pinMessageUseCase(
        MessageMutationGuard mutationGuard,
        MessageStore messages
    ) {
        return new PinMessageUseCase(mutationGuard, messages, Clock.systemUTC());
    }

    @Bean
    ChannelMessageQueryService channelMessageQueryService(
        ChannelMessageReadGuard readGuard,
        ChannelMessageReadModelPort readModels
    ) {
        return new ChannelMessageQueryService(readGuard, readModels);
    }

    @Bean
    MessagePublicationRelay messagePublicationRelay(
        MessagePublicationOutboxQueue outbox,
        MessagePublishedDispatcher dispatcher,
        @Value("${discord.message.outbox-relay-retry-delay-ms:5000}") long retryDelayMs
    ) {
        return new DefaultMessagePublicationRelay(
            outbox,
            dispatcher,
            Clock.systemUTC(),
            Duration.ofSeconds(30),
            Duration.ofMillis(retryDelayMs)
        );
    }

    @Bean
    @Profile("!kafka")
    MessagePublishedDispatcher messagePublishedDispatcher(
        InMemoryGatewayService gatewayService,
        MessageLookupPort messages
    ) {
        return event -> {
            if (event.target() instanceof ChannelMessageTarget channel) {
                Message message = messages.requireMessage(channel, event.messageId());
                gatewayService.publish(
                    event.eventId(),
                    "MESSAGE_CREATE",
                    channel.guildId(),
                    channel.channelId(),
                    gatewayPayload(message)
                );
            }
        };
    }

    @Bean
    @Profile("!postgres")
    MessageAuthorizationPolicy messageAuthorizationPolicy(InMemoryGuildService guildService) {
        return new MessageAuthorizationPolicy(guildService);
    }

    @Bean
    @Profile("postgres")
    MessageAuthorizationPolicy projectedMessageAuthorizationPolicy(
        InMemoryGuildService guildService,
        AuthorizationProjectionStore projections,
        @Value("${discord.authz.projection-enabled:false}") boolean projectionEnabled
    ) {
        return new MessageAuthorizationPolicy(guildService, projections, projectionEnabled);
    }

    @Bean
    @Profile("kafka")
    MessagePublishedDispatcher kafkaMessagePublishedDispatcher(
        KafkaTemplate<String, String> kafka,
        ObjectMapper objectMapper,
        MessageLookupPort messages,
        @Value("${discord.kafka.topic-prefix:discord}") String topicPrefix,
        @Value("${discord.kafka.message-publish-timeout-ms:5000}") long publishTimeoutMillis
    ) {
        return new KafkaMessagePublishedDispatcher(
            kafka,
            objectMapper,
            messages,
            Clock.systemUTC(),
            topicPrefix,
            publishTimeoutMillis
        );
    }

    @Bean
    MessageContentPolicy messageContentPolicy(InMemoryModerationService moderationService) {
        return new MessageContentModerationPolicy(moderationService);
    }

    @Bean
    @Profile("!postgres")
    MessagePublicationOutbox messagePublicationOutbox() {
        return event -> {
        };
    }

    private static Map<String, Object> gatewayPayload(Message message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", message.id().toString());
        payload.put("guildId", message.guildId().toString());
        payload.put("channelId", message.channelId().toString());
        payload.put("authorId", message.authorId().toString());
        payload.put("content", message.content().value());
        payload.put("mentions", message.mentions().stream().map(MessageConfiguration::mentionToken).toList());
        payload.put("pinned", message.pinned());
        payload.put("deleted", message.deleted());
        payload.put("edited", message.edited());
        payload.put("createdAt", message.createdAt().toString());
        payload.put("updatedAt", message.updatedAt().toString());
        return payload;
    }

    private static String mentionToken(MessageMentionTarget mention) {
        return switch (mention) {
            case UserMentionTarget user -> user.userId().toString();
            case RoleMentionTarget role -> role.roleId().toString();
            case ChannelMentionTarget channel -> channel.channelId().toString();
            case SpecialMentionTarget special -> special.kind().name().toLowerCase(Locale.ROOT);
        };
    }
}
