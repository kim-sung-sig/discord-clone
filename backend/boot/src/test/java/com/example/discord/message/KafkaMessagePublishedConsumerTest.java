package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.discord.gateway.GatewayBusEvent;
import com.example.discord.gateway.GatewayEvent;
import com.example.discord.gateway.GatewayEventLog;
import com.example.discord.gateway.InMemoryGatewayEventBus;
import com.example.discord.gateway.InMemoryGatewayEventLog;
import com.example.discord.gateway.InMemoryGatewayService;
import com.example.discord.gateway.InMemoryGatewaySessionCursorStore;
import com.example.discord.gateway.InMemoryGatewaySessionRegistry;
import com.example.discord.guild.InMemoryGuildService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessResourceFailureException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        KafkaMessagePublishedConsumer consumer = new KafkaMessagePublishedConsumer(
            new ObjectMapper().findAndRegisterModules(),
            (target, ignored) -> message,
            gateway,
            new InMemoryMessagePublicationInbox(),
            metrics
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
        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "processed").counter().count())
            .isEqualTo(1);
        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "duplicate").counter().count())
            .isEqualTo(1);
        assertThat(metrics.find("discord.message.publication.end_to_end").timer()).isNotNull();
    }

    @Test
    void redeliversAfterConsumerRestartWhenProcessStopsBeforeGatewaySideEffect() throws Exception {
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
        AtomicBoolean failBeforeLookup = new AtomicBoolean(true);
        MessageLookupPort lookup = (target, ignored) -> {
            if (failBeforeLookup.compareAndSet(true, false)) {
                throw new AssertionError("simulated consumer process stop");
            }
            return message;
        };
        InMemoryMessagePublicationInbox persistentInbox = new InMemoryMessagePublicationInbox();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String payload = mapper.writeValueAsString(MessagePublishedRecord.from(new MessagePublished(
            eventId,
            messageId,
            message.author(),
            message.target(),
            message.mentions(),
            "correlation",
            message.createdAt()
        ), "payload-hash"));

        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        KafkaMessagePublishedConsumer firstProcess = new KafkaMessagePublishedConsumer(mapper, lookup, gateway, persistentInbox, metrics);
        assertThatThrownBy(() -> firstProcess.consume(payload)).isInstanceOf(AssertionError.class);

        KafkaMessagePublishedConsumer restartedProcess = new KafkaMessagePublishedConsumer(mapper, lookup, gateway, persistentInbox, metrics);
        restartedProcess.consume(payload);

        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().eventId()).isEqualTo(eventId.toString());
        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "processed").counter().count())
            .isEqualTo(1);
    }

    @Test
    void replaysAfterProcessStopsBetweenGatewayAppendAndInboxCompletion() throws Exception {
        UUID userId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("messages", userId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);
        List<GatewayBusEvent> sent = new ArrayList<>();
        InMemoryGatewayEventBus eventBus = new InMemoryGatewayEventBus(CLOCK);
        InMemoryGatewayService gateway = new InMemoryGatewayService(guilds, CLOCK, Duration.ofSeconds(30), eventBus);
        gateway.addEventListener(event -> sent.add(new GatewayBusEvent(
            event.busEventId(), event.type(), event.guildId(), event.channelId(), event.payload(), event.createdAt())));
        Message message = new Message(
            UUID.randomUUID(), new UserMessageAuthor(userId), new ChannelMessageTarget(guild.id(), channel.id()),
            new MessageContent("crash after gateway append"), List.of(), false, false, List.of(), CLOCK.instant(), CLOCK.instant());
        UUID eventId = UUID.randomUUID();
        AtomicBoolean stopAfterSideEffect = new AtomicBoolean(true);
        java.util.Set<UUID> processed = new java.util.HashSet<>();
        MessagePublicationInbox inbox = new MessagePublicationInbox() {
            @Override
            public boolean isProcessed(UUID id) {
                return processed.contains(id);
            }

            @Override
            public void markProcessed(UUID id) {
                if (stopAfterSideEffect.compareAndSet(true, false)) {
                    throw new AssertionError("simulated process stop before inbox completion");
                }
                processed.add(id);
            }
        };
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String payload = mapper.writeValueAsString(MessagePublishedRecord.from(new MessagePublished(
            eventId, message.id(), message.author(), message.target(), message.mentions(), "correlation", CLOCK.instant()
        ), "payload-hash"));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        KafkaMessagePublishedConsumer beforeRestart = new KafkaMessagePublishedConsumer(
            mapper, (target, ignored) -> message, gateway, inbox, metrics);
        KafkaMessagePublishedConsumer afterRestart = new KafkaMessagePublishedConsumer(
            mapper, (target, ignored) -> message, gateway, inbox, metrics);

        assertThatThrownBy(() -> beforeRestart.consume(payload)).isInstanceOf(AssertionError.class);
        afterRestart.consume(payload);

        assertThat(processed).containsExactly(eventId);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().eventId()).isEqualTo(eventId.toString());
    }

    @Test
    void redeliveryAfterMessageEditCompletesInboxWithoutRepublishingTheGatewayEvent() throws Exception {
        UUID userId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("messages", userId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);
        List<GatewayBusEvent> sent = new ArrayList<>();
        InMemoryGatewayService gateway = new InMemoryGatewayService(
            guilds, CLOCK, Duration.ofSeconds(30), new InMemoryGatewayEventBus(CLOCK));
        gateway.addEventListener(event -> sent.add(new GatewayBusEvent(
            event.busEventId(), event.type(), event.guildId(), event.channelId(), event.payload(), event.createdAt())));
        Message original = new Message(
            UUID.randomUUID(), new UserMessageAuthor(userId), new ChannelMessageTarget(guild.id(), channel.id()),
            new MessageContent("before edit"), List.of(), false, false, List.of(), CLOCK.instant(), CLOCK.instant());
        Message edited = new Message(
            original.id(), original.author(), original.target(), new MessageContent("after edit"),
            List.of(), false, false, List.of(), CLOCK.instant(), CLOCK.instant().plusSeconds(5));
        java.util.concurrent.atomic.AtomicReference<Message> current = new java.util.concurrent.atomic.AtomicReference<>(original);
        AtomicBoolean failInboxCompletion = new AtomicBoolean(true);
        java.util.Set<UUID> processed = new java.util.HashSet<>();
        MessagePublicationInbox inbox = new MessagePublicationInbox() {
            @Override
            public boolean isProcessed(UUID id) {
                return processed.contains(id);
            }

            @Override
            public void markProcessed(UUID id) {
                if (failInboxCompletion.compareAndSet(true, false)) {
                    throw new DataAccessResourceFailureException("simulated inbox write failure");
                }
                processed.add(id);
            }
        };
        UUID eventId = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String payload = mapper.writeValueAsString(MessagePublishedRecord.from(new MessagePublished(
            eventId, original.id(), original.author(), original.target(), original.mentions(), "correlation", CLOCK.instant()
        ), "payload-hash"));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        KafkaMessagePublishedConsumer consumer = new KafkaMessagePublishedConsumer(
            mapper, (target, ignored) -> current.get(), gateway, inbox, metrics);

        assertThatThrownBy(() -> consumer.consume(payload)).isInstanceOf(DataAccessResourceFailureException.class);
        current.set(edited);
        consumer.consume(payload);

        assertThat(processed).containsExactly(eventId);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().payload()).containsEntry("content", "before edit");
        assertThat(metrics.get("discord.message.publication.consumer.events").tag("outcome", "processed").counter().count())
            .isEqualTo(1);
        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "duplicate").counter())
            .isNull();
    }

    @Test
    void countsMalformedEventsAsFailed() {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        KafkaMessagePublishedConsumer consumer = new KafkaMessagePublishedConsumer(
            new ObjectMapper(),
            (target, messageId) -> { throw new AssertionError("lookup must not run"); },
            new InMemoryGatewayService(
                new InMemoryGuildService(), CLOCK, Duration.ofSeconds(30), new InMemoryGatewayEventBus(CLOCK)),
            new InMemoryMessagePublicationInbox(),
            metrics
        );

        assertThatThrownBy(() -> consumer.consume("{broken"))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "failed").counter().count())
            .isEqualTo(1);
    }

    @Test
    void jsonNullRecordIsRejectedAsInvalidWithoutRetryableNpe() {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        KafkaMessagePublishedConsumer consumer = new KafkaMessagePublishedConsumer(
            new ObjectMapper(),
            (target, messageId) -> { throw new AssertionError("lookup must not run"); },
            new InMemoryGatewayService(
                new InMemoryGuildService(), CLOCK, Duration.ofSeconds(30), new InMemoryGatewayEventBus(CLOCK)),
            new InMemoryMessagePublicationInbox(),
            metrics
        );

        // The shared error handler dead-letters IllegalArgumentException at once; an NPE would be retried twice.
        assertThatThrownBy(() -> consumer.consume("null"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("invalid message publication record");
        assertThat(metrics.get("discord.message.publication.consumer.events").tag("outcome", "failed").counter().count())
            .isEqualTo(1);
    }

    @Test
    void concurrentAppendConflictCompletesInboxWithoutSecondListenerNotification() throws Exception {
        UUID userId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("messages", userId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);
        InMemoryGatewayEventLog durableLog = new InMemoryGatewayEventLog();
        // Reports "not appended" even after the append, like a delivery that checked just before another one appended.
        GatewayEventLog checkedBeforeConcurrentAppend = new GatewayEventLog() {
            @Override
            public long allocateSequence() {
                return durableLog.allocateSequence();
            }

            @Override
            public GatewayEvent append(GatewayBusEvent event) {
                return durableLog.append(event);
            }

            @Override
            public boolean contains(String eventId) {
                return false;
            }

            @Override
            public List<GatewayEvent> after(long sequence, int limit) {
                return durableLog.after(sequence, limit);
            }

            @Override
            public long oldestSequence() {
                return durableLog.oldestSequence();
            }

            @Override
            public long latestSequence() {
                return durableLog.latestSequence();
            }
        };
        InMemoryGatewayEventBus bus = new InMemoryGatewayEventBus(CLOCK);
        List<GatewayBusEvent> busSent = new ArrayList<>();
        bus.addEventListener(busSent::add);
        InMemoryGatewayService gateway = new InMemoryGatewayService(
            guilds, CLOCK, Duration.ofSeconds(30), bus,
            new InMemoryGatewaySessionRegistry(), null, false, checkedBeforeConcurrentAppend,
            new InMemoryGatewaySessionCursorStore());
        List<GatewayBusEvent> sent = new ArrayList<>();
        gateway.addEventListener(event -> sent.add(new GatewayBusEvent(
            event.busEventId(), event.type(), event.guildId(), event.channelId(), event.payload(), event.createdAt())));
        Message original = new Message(
            UUID.randomUUID(), new UserMessageAuthor(userId), new ChannelMessageTarget(guild.id(), channel.id()),
            new MessageContent("first delivery"), List.of(), false, false, List.of(), CLOCK.instant(), CLOCK.instant());
        Message edited = new Message(
            original.id(), original.author(), original.target(), new MessageContent("second delivery"),
            List.of(), false, false, List.of(), CLOCK.instant(), CLOCK.instant().plusSeconds(5));
        java.util.concurrent.atomic.AtomicReference<Message> current = new java.util.concurrent.atomic.AtomicReference<>(original);
        AtomicBoolean failInboxCompletion = new AtomicBoolean(true);
        java.util.Set<UUID> processed = new java.util.HashSet<>();
        MessagePublicationInbox inbox = new MessagePublicationInbox() {
            @Override
            public boolean isProcessed(UUID id) {
                return processed.contains(id);
            }

            @Override
            public void markProcessed(UUID id) {
                if (failInboxCompletion.compareAndSet(true, false)) {
                    throw new DataAccessResourceFailureException("simulated inbox write failure");
                }
                processed.add(id);
            }
        };
        UUID eventId = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String payload = mapper.writeValueAsString(MessagePublishedRecord.from(new MessagePublished(
            eventId, original.id(), original.author(), original.target(), original.mentions(), "correlation", CLOCK.instant()
        ), "payload-hash"));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        KafkaMessagePublishedConsumer consumer = new KafkaMessagePublishedConsumer(
            mapper, (target, ignored) -> current.get(), gateway, inbox, metrics);

        assertThatThrownBy(() -> consumer.consume(payload)).isInstanceOf(DataAccessResourceFailureException.class);
        current.set(edited);
        consumer.consume(payload);

        assertThat(processed).containsExactly(eventId);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().payload()).containsEntry("content", "first delivery");
        assertThat(durableLog.after(0L, 10)).hasSize(1);
        assertThat(durableLog.after(0L, 10).getFirst().payload()).containsEntry("content", "first delivery");
        // The bus publish happens before the append detects the conflict, so the edited payload still reaches the bus
        // once more. Only the durable log and service listeners are protected (see the T216 blueprint race analysis).
        assertThat(busSent).extracting(event -> event.payload().get("content"))
            .containsExactly("first delivery", "second delivery");
        assertThat(metrics.get("discord.message.publication.consumer.events").tag("outcome", "processed").counter().count())
            .isEqualTo(1);
        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "duplicate").counter())
            .isNull();
    }

    @Test
    void databaseFailureCanBeRetriedWithoutLosingOrDuplicatingTheGatewayEvent() throws Exception {
        UUID userId = UUID.randomUUID();
        InMemoryGuildService guilds = new InMemoryGuildService();
        var guild = guilds.createGuild("messages", userId);
        var channel = guilds.createChannel(guild.id(), "general", com.example.discord.channel.ChannelType.GUILD_TEXT, null);
        List<GatewayBusEvent> sent = new ArrayList<>();
        InMemoryGatewayService gateway = new InMemoryGatewayService(
            guilds, CLOCK, Duration.ofSeconds(30), new InMemoryGatewayEventBus(CLOCK));
        gateway.addEventListener(event -> sent.add(new GatewayBusEvent(
            event.busEventId(), event.type(), event.guildId(), event.channelId(), event.payload(), event.createdAt())));
        Message message = new Message(
            UUID.randomUUID(), new UserMessageAuthor(userId), new ChannelMessageTarget(guild.id(), channel.id()),
            new MessageContent("recover db outage"), List.of(), false, false, List.of(), CLOCK.instant(), CLOCK.instant());
        AtomicBoolean databaseUnavailable = new AtomicBoolean(true);
        MessageLookupPort lookup = (target, messageId) -> {
            if (databaseUnavailable.get()) {
                throw new DataAccessResourceFailureException("simulated database outage");
            }
            return message;
        };
        UUID eventId = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String payload = mapper.writeValueAsString(MessagePublishedRecord.from(new MessagePublished(
            eventId, message.id(), message.author(), message.target(), message.mentions(), "correlation", CLOCK.instant()
        ), "payload-hash"));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        InMemoryMessagePublicationInbox inbox = new InMemoryMessagePublicationInbox();
        KafkaMessagePublishedConsumer consumer = new KafkaMessagePublishedConsumer(mapper, lookup, gateway, inbox, metrics);

        assertThatThrownBy(() -> consumer.consume(payload)).isInstanceOf(DataAccessResourceFailureException.class);
        databaseUnavailable.set(false);
        consumer.consume(payload);
        consumer.consume(payload);

        assertThat(sent).hasSize(1);
        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "failed").counter().count())
            .isEqualTo(1);
        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "processed").counter().count())
            .isEqualTo(1);
        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "duplicate").counter().count())
            .isEqualTo(1);
    }
}
