package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.channel.ChannelType;
import com.example.discord.guild.Channel;
import com.example.discord.guild.Guild;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.guild.Role;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class InMemoryGatewayServiceTest {
    private static final int EXPECTED_MAX_RETAINED_EVENTS = 1_000;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-05-13T00:00:00Z"));
    private final InMemoryGuildService guildService = new InMemoryGuildService();
    private final InMemoryGatewayService gatewayService =
        new InMemoryGatewayService(guildService, clock, Duration.ofSeconds(30));

    @Test
    void identifyReturnsReadyEventForUserGuilds() {
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);

        GatewayIdentifyResult result = gatewayService.identify(ownerId);

        assertThat(result.session().userId()).isEqualTo(ownerId);
        assertThat(result.session().guildIds()).containsExactly(guild.id());
        assertThat(result.ready().type()).isEqualTo("READY");
        assertThat(result.ready().sequence()).isEqualTo(1L);
        assertThat(result.ready().payload()).containsEntry("sessionId", result.session().id().toString());
    }

    @Test
    void identifyStartsAfterExistingBacklog() {
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        gatewayService.publish("GUILD_UPDATE", guild.id(), null, Map.of("name", "old"));

        GatewayIdentifyResult result = gatewayService.identify(ownerId);

        assertThat(gatewayService.poll(result.session().id(), ownerId, 0L)).isEmpty();
    }

    @Test
    void heartbeatUpdatesAckTimestampAndReturnsAckEvent() {
        UUID ownerId = UUID.randomUUID();
        guildService.createGuild("Discord Clone", ownerId);
        GatewayIdentifyResult identified = gatewayService.identify(ownerId);
        clock.advance(Duration.ofSeconds(5));

        GatewayHeartbeatResult result = gatewayService.heartbeat(identified.session().id(), ownerId);

        assertThat(result.session().lastAcknowledgedAt()).isEqualTo(clock.instant());
        assertThat(result.ack().type()).isEqualTo("HEARTBEAT_ACK");
        assertThat(result.ack().payload()).containsEntry("sessionId", identified.session().id().toString());
    }

    @Test
    void scanTimeoutsClosesSessionsPastHeartbeatWindow() {
        UUID ownerId = UUID.randomUUID();
        guildService.createGuild("Discord Clone", ownerId);
        GatewayIdentifyResult identified = gatewayService.identify(ownerId);

        clock.advance(Duration.ofSeconds(31));
        List<GatewaySession> closed = gatewayService.closeTimedOutSessions();

        assertThat(closed).extracting(GatewaySession::id).containsExactly(identified.session().id());
        assertThat(gatewayService.session(identified.session().id()).closed()).isTrue();
    }

    @Test
    void closedSessionsCannotHeartbeatPollOrResume() {
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        GatewayIdentifyResult identified = gatewayService.identify(ownerId);
        clock.advance(Duration.ofSeconds(31));
        gatewayService.closeTimedOutSessions();
        gatewayService.publish("GUILD_UPDATE", guild.id(), null, Map.of("name", "after close"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gatewayService.heartbeat(identified.session().id(), ownerId))
            .isInstanceOf(GatewayForbiddenException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gatewayService.poll(identified.session().id(), ownerId, 0L))
            .isInstanceOf(GatewayForbiddenException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gatewayService.resume(identified.session().id(), ownerId, 0L))
            .isInstanceOf(GatewayForbiddenException.class);
    }

    @Test
    void publishedEventsUseGlobalMonotonicSequences() {
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);

        GatewayEvent first = gatewayService.publish("GUILD_UPDATE", guild.id(), null, Map.of("name", "one"));
        GatewayEvent second = gatewayService.publish("GUILD_UPDATE", guild.id(), null, Map.of("name", "two"));

        assertThat(second.sequence()).isGreaterThan(first.sequence());
        assertThat(List.of(first.sequence(), second.sequence())).containsExactly(1L, 2L);
    }

    @Test
    void resumeReturnsOnlyEventsAfterRequestedSequence() {
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        GatewayIdentifyResult identified = gatewayService.identify(ownerId);
        GatewayEvent before = gatewayService.publish("GUILD_UPDATE", guild.id(), null, Map.of("name", "before"));
        GatewayEvent after = gatewayService.publish("GUILD_UPDATE", guild.id(), null, Map.of("name", "after"));

        GatewayResumeResult result = gatewayService.resume(identified.session().id(), ownerId, before.sequence());

        assertThat(result.resumed().type()).isEqualTo("RESUMED");
        assertThat(result.events()).extracting(GatewayEvent::sequence).containsExactly(after.sequence());
    }

    @Test
    void pollingFiltersEventsAlreadyDeliveredToSession() {
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        GatewayIdentifyResult identified = gatewayService.identify(ownerId);
        GatewayEvent event = gatewayService.publish("GUILD_UPDATE", guild.id(), null, Map.of("name", "once"));

        List<GatewayEvent> firstPoll = gatewayService.poll(identified.session().id(), ownerId, 0L);
        List<GatewayEvent> secondPoll = gatewayService.poll(identified.session().id(), ownerId, 0L);

        assertThat(firstPoll).extracting(GatewayEvent::sequence).containsExactly(event.sequence());
        assertThat(secondPoll).isEmpty();
    }

    @Test
    void pollDoesNotReplayMoreThanRetainedEventWindow() {
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        GatewayIdentifyResult identified = gatewayService.identify(ownerId);

        for (int index = 0; index <= EXPECTED_MAX_RETAINED_EVENTS; index++) {
            gatewayService.publish("GUILD_UPDATE", guild.id(), null, Map.of("name", "event-" + index));
        }

        List<GatewayEvent> delivered = gatewayService.poll(identified.session().id(), ownerId, 0L);
        assertThat(delivered).hasSize(EXPECTED_MAX_RETAINED_EVENTS);
        assertThat(delivered)
            .extracting(event -> event.payload().get("name"))
            .doesNotContain("event-0")
            .contains("event-1", "event-" + EXPECTED_MAX_RETAINED_EVENTS);
    }

    @Test
    void pollingFiltersChannelEventsHiddenFromSessionUser() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        Channel visible = guildService.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Channel hidden = guildService.createChannel(guild.id(), "staff", ChannelType.GUILD_TEXT, null);
        guildService.addMember(guild.id(), memberId);
        denyEveryoneView(guild, hidden);
        GatewayIdentifyResult identified = gatewayService.identify(memberId);
        GatewayEvent visibleEvent = gatewayService.publish(
            "MESSAGE_CREATE",
            guild.id(),
            visible.id(),
            Map.of("content", "visible")
        );
        gatewayService.publish("MESSAGE_CREATE", guild.id(), hidden.id(), Map.of("content", "hidden"));

        List<GatewayEvent> events = gatewayService.poll(identified.session().id(), memberId, 0L);

        assertThat(events).extracting(GatewayEvent::sequence).containsExactly(visibleEvent.sequence());
    }

    @Test
    void sharedBusFansOutEventsToSessionsOnOtherGatewayNodes() {
        InMemoryGatewayEventBus eventBus = new InMemoryGatewayEventBus(clock);
        InMemoryGatewayService nodeA = new InMemoryGatewayService(guildService, clock, Duration.ofSeconds(30), eventBus);
        InMemoryGatewayService nodeB = new InMemoryGatewayService(guildService, clock, Duration.ofSeconds(30), eventBus);
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        Channel channel = guildService.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        GatewayIdentifyResult identifiedOnNodeB = nodeB.identify(ownerId);

        GatewayEvent publishedOnNodeA = nodeA.publish(
            "MESSAGE_CREATE",
            guild.id(),
            channel.id(),
            Map.of("content", "cross-node")
        );

        List<GatewayEvent> deliveredOnNodeB = nodeB.poll(identifiedOnNodeB.session().id(), ownerId, 0L);
        assertThat(deliveredOnNodeB)
            .extracting(GatewayEvent::type)
            .containsExactly("MESSAGE_CREATE");
        assertThat(deliveredOnNodeB)
            .extracting(event -> event.payload().get("content"))
            .containsExactly("cross-node");
        assertThat(deliveredOnNodeB)
            .extracting(GatewayEvent::busEventId)
            .containsExactly(publishedOnNodeA.busEventId());
    }

    @Test
    void sharedSessionRegistryAllowsResumeOnDifferentGatewayNode() {
        InMemoryGatewayEventBus eventBus = new InMemoryGatewayEventBus(clock);
        InMemoryGatewaySessionRegistry sessionRegistry = new InMemoryGatewaySessionRegistry();
        InMemoryGatewayService nodeA = new InMemoryGatewayService(
            guildService,
            clock,
            Duration.ofSeconds(30),
            eventBus,
            sessionRegistry
        );
        InMemoryGatewayService nodeB = new InMemoryGatewayService(
            guildService,
            clock,
            Duration.ofSeconds(30),
            eventBus,
            sessionRegistry
        );
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        Channel channel = guildService.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        GatewayIdentifyResult identifiedOnNodeA = nodeA.identify(ownerId);
        GatewayEvent publishedOnNodeA = nodeA.publish(
            "MESSAGE_CREATE",
            guild.id(),
            channel.id(),
            Map.of("content", "missed while reconnecting")
        );

        GatewayResumeResult resumedOnNodeB = nodeB.resume(identifiedOnNodeA.session().id(), ownerId, 0L);

        assertThat(resumedOnNodeB.resumed().type()).isEqualTo("RESUMED");
        assertThat(resumedOnNodeB.events()).extracting(GatewayEvent::busEventId).containsExactly(publishedOnNodeA.busEventId());
        assertThat(resumedOnNodeB.events())
            .extracting(event -> event.payload().get("content"))
            .containsExactly("missed while reconnecting");
        assertThat(sessionRegistry.find(identifiedOnNodeA.session().id()).orElseThrow().lastDeliveredSequence())
            .isEqualTo(resumedOnNodeB.events().getFirst().sequence());
    }

    @Test
    void resumeOnDifferentNodeRegistersVisibleChannelSubscriptions() {
        RecordingGatewayEventBus nodeABus = new RecordingGatewayEventBus();
        RecordingGatewayEventBus nodeBBus = new RecordingGatewayEventBus();
        InMemoryGatewaySessionRegistry sessionRegistry = new InMemoryGatewaySessionRegistry();
        InMemoryGatewayService nodeA = new InMemoryGatewayService(
            guildService,
            clock,
            Duration.ofSeconds(30),
            nodeABus,
            sessionRegistry
        );
        InMemoryGatewayService nodeB = new InMemoryGatewayService(
            guildService,
            clock,
            Duration.ofSeconds(30),
            nodeBBus,
            sessionRegistry
        );
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        Channel channel = guildService.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        GatewayIdentifyResult identifiedOnNodeA = nodeA.identify(ownerId);

        nodeB.resume(identifiedOnNodeA.session().id(), ownerId, 0L);

        assertThat(nodeBBus.subscribedGuilds()).contains(guild.id());
        assertThat(nodeBBus.subscribedChannels()).contains(channel.id());
    }

    @Test
    void pollReconcilesChannelsThatBecameVisibleAfterIdentify() {
        RecordingGatewayEventBus eventBus = new RecordingGatewayEventBus();
        InMemoryGatewayService node = new InMemoryGatewayService(
            guildService,
            clock,
            Duration.ofSeconds(30),
            eventBus
        );
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        Channel hidden = guildService.createChannel(guild.id(), "staff", ChannelType.GUILD_TEXT, null);
        guildService.addMember(guild.id(), memberId);
        denyEveryoneView(guild, hidden);
        GatewayIdentifyResult identified = node.identify(memberId);
        assertThat(eventBus.subscribedChannels()).doesNotContain(hidden.id());
        Role staffRole = guildService.createRole(
            guild.id(),
            "staff",
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
        );
        guildService.assignRoleToMember(guild.id(), memberId, staffRole.id());
        guildService.addChannelRoleOverwrite(
            guild.id(),
            hidden.id(),
            staffRole.id(),
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL),
            PermissionSet.empty()
        );

        node.poll(identified.session().id(), memberId, 0L);

        assertThat(eventBus.subscribedChannels()).contains(hidden.id());
    }

    @Test
    void crossNodeFanoutStillFiltersHiddenChannelEventsAtDeliveryTime() {
        InMemoryGatewayEventBus eventBus = new InMemoryGatewayEventBus(clock);
        InMemoryGatewayService nodeA = new InMemoryGatewayService(guildService, clock, Duration.ofSeconds(30), eventBus);
        InMemoryGatewayService nodeB = new InMemoryGatewayService(guildService, clock, Duration.ofSeconds(30), eventBus);
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        Channel visible = guildService.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Channel hidden = guildService.createChannel(guild.id(), "staff", ChannelType.GUILD_TEXT, null);
        guildService.addMember(guild.id(), memberId);
        denyEveryoneView(guild, hidden);
        GatewayIdentifyResult identifiedOnNodeB = nodeB.identify(memberId);
        GatewayEvent visibleEvent = nodeA.publish(
            "MESSAGE_CREATE",
            guild.id(),
            visible.id(),
            Map.of("content", "visible cross-node")
        );
        nodeA.publish("MESSAGE_CREATE", guild.id(), hidden.id(), Map.of("content", "hidden cross-node"));

        List<GatewayEvent> deliveredOnNodeB = nodeB.poll(identifiedOnNodeB.session().id(), memberId, 0L);

        assertThat(deliveredOnNodeB).extracting(GatewayEvent::busEventId).containsExactly(visibleEvent.busEventId());
    }

    @Test
    void busRedeliveryDoesNotAppendDuplicateGatewayEvents() {
        InMemoryGatewayEventBus eventBus = new InMemoryGatewayEventBus(clock);
        InMemoryGatewayService node = new InMemoryGatewayService(guildService, clock, Duration.ofSeconds(30), eventBus);
        UUID ownerId = UUID.randomUUID();
        Guild guild = guildService.createGuild("Discord Clone", ownerId);
        GatewayIdentifyResult identified = node.identify(ownerId);
        GatewayEvent published = node.publish("GUILD_UPDATE", guild.id(), null, Map.of("name", "once"));

        eventBus.redeliver(published.busEventId());

        List<GatewayEvent> delivered = node.poll(identified.session().id(), ownerId, 0L);
        assertThat(delivered).extracting(GatewayEvent::busEventId).containsExactly(published.busEventId());
    }

    private void denyEveryoneView(Guild guild, Channel channel) {
        guildService.addChannelRoleOverwrite(
            guild.id(),
            channel.id(),
            guild.everyoneRole().id(),
            PermissionSet.empty(),
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
        );
    }

    private static final class RecordingGatewayEventBus implements GatewayEventBus {
        private final Set<UUID> subscribedGuilds = new java.util.LinkedHashSet<>();
        private final Set<UUID> subscribedChannels = new java.util.LinkedHashSet<>();
        private final List<Consumer<GatewayBusEvent>> listeners = new java.util.ArrayList<>();

        @Override
        public GatewayBusEvent publish(GatewayBusPublishCommand command) {
            GatewayBusEvent event = new GatewayBusEvent(
                UUID.randomUUID().toString(),
                command.type(),
                command.guildId(),
                command.channelId(),
                command.payload(),
                Instant.parse("2026-05-13T00:00:00Z")
            );
            for (Consumer<GatewayBusEvent> listener : List.copyOf(listeners)) {
                listener.accept(event);
            }
            return event;
        }

        @Override
        public void addEventListener(Consumer<GatewayBusEvent> listener) {
            listeners.add(listener);
        }

        @Override
        public void subscribeGuild(UUID guildId) {
            subscribedGuilds.add(guildId);
        }

        @Override
        public void subscribeChannel(UUID channelId) {
            subscribedChannels.add(channelId);
        }

        Set<UUID> subscribedGuilds() {
            return Set.copyOf(subscribedGuilds);
        }

        Set<UUID> subscribedChannels() {
            return Set.copyOf(subscribedChannels);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
