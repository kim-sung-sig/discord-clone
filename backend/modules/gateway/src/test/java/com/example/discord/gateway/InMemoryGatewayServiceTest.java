package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.channel.ChannelType;
import com.example.discord.guild.Channel;
import com.example.discord.guild.Guild;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryGatewayServiceTest {
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

    private void denyEveryoneView(Guild guild, Channel channel) {
        guildService.addChannelRoleOverwrite(
            guild.id(),
            channel.id(),
            guild.everyoneRole().id(),
            PermissionSet.empty(),
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
        );
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
