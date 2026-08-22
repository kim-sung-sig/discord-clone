package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.gateway.GatewayBusEvent;
import com.example.discord.gateway.InMemoryGatewayEventBus;
import com.example.discord.gateway.InMemoryGatewayService;
import com.example.discord.guild.InMemoryGuildService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KafkaMessagePublishedConsumerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void inboxDeduplicatesGatewaySideEffectAndKeepsOriginalEventId() throws Exception {
        UUID userId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("messages", userId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);
        List<GatewayBusEvent> sent = new ArrayList<>();
        InMemoryGatewayService gateway = new InMemoryGatewayService(
            guilds,
            CLOCK,
            Duration.ofSeconds(30),
            new InMemoryGatewayEventBus(CLOCK)
        );
        gateway.addEventListener(event -> sent.add(new GatewayBusEvent(
            event.busEventId(), event.type(), event.guildId(), event.channelId(), event.payload(), event.createdAt())));
        UUID messageId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Message message = new Message(
            messageId,
            new UserMessageAuthor(userId),
            new ChannelMessageTarget(guild.id(), channel.id()),
            new MessageContent("hello"),
            List.of(),
            false,
            false,
            List.of(),
            CLOCK.instant(),
            CLOCK.instant()
        );
        KafkaMessagePublishedConsumer consumer = new KafkaMessagePublishedConsumer(
            new ObjectMapper().findAndRegisterModules(),
            (target, ignored) -> message,
            gateway,
            new InMemoryMessagePublicationInbox()
        );
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(
            MessagePublishedRecord.from(new MessagePublished(
                eventId,
                messageId,
                message.author(),
                message.target(),
                message.mentions(),
                "correlation",
                message.createdAt()
            ), "payload-hash")
        );

        consumer.consume(payload);
        consumer.consume(payload);

        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().eventId()).isEqualTo(eventId.toString());
    }
}
