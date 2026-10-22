package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.channel.ChannelType;
import com.example.discord.guild.Channel;
import com.example.discord.guild.Guild;
import com.example.discord.guild.GuildService;
import com.example.discord.guild.GuildStore;
import com.example.discord.guild.InMemoryGuildService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class InMemoryGatewayServiceGuildReadTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);
    private final InMemoryGuildService seed = new InMemoryGuildService();
    private final CountingStore store = new CountingStore(seed);
    private final UUID ownerId = UUID.randomUUID();
    private final Guild guild = seed.createGuild("gateway", ownerId);
    private final List<UUID> users = new ArrayList<>(List.of(ownerId));

    @Test
    void publishReadsTheGuildOnceWhileEveryConnectedSessionPolls() {
        Channel channel = channelsWithMembers(4, 3).getFirst();
        InMemoryGatewayService gateway = new InMemoryGatewayService(new GuildService(store), clock, Duration.ofSeconds(30));
        List<GatewaySession> sessions = users.stream().map(user -> gateway.identify(user).session()).toList();
        Map<UUID, List<GatewayEvent>> delivered = new java.util.HashMap<>();
        gateway.addEventListener(ignored -> sessions.forEach(session ->
            delivered.put(session.userId(), gateway.poll(session.id(), session.userId(), 0L))));
        store.reads = 0;

        gateway.publish("MESSAGE_CREATE", guild.id(), channel.id(), Map.of("content", "hello"));

        assertThat(store.reads).isEqualTo(1);
        assertThat(delivered).hasSize(users.size());
        assertThat(delivered.values()).allSatisfy(events ->
            assertThat(events).extracting(GatewayEvent::type).containsExactly("MESSAGE_CREATE"));
    }

    @Test
    void pollReadsEachGuildOnceRegardlessOfBacklogSize() {
        Channel channel = channelsWithMembers(1, 2).getFirst();
        InMemoryGatewayService gateway = new InMemoryGatewayService(new GuildService(store), clock, Duration.ofSeconds(30));
        GatewaySession session = gateway.identify(users.get(1)).session();
        for (int i = 0; i < 20; i++) {
            gateway.publish("MESSAGE_CREATE", guild.id(), channel.id(), Map.of("index", i));
        }
        store.reads = 0;

        assertThat(gateway.poll(session.id(), session.userId(), 0L)).hasSize(20);
        assertThat(store.reads).isEqualTo(1);
    }

    @Test
    void subscriptionTrackingBusStillSubscribesVisibleChannelsWithOneGuildRead() {
        List<Channel> channels = channelsWithMembers(1, 3);
        SubscriptionRecordingBus bus = new SubscriptionRecordingBus(clock);
        InMemoryGatewayService gateway = new InMemoryGatewayService(new GuildService(store), clock, Duration.ofSeconds(30), bus);
        GatewaySession session = gateway.identify(users.get(1)).session();
        bus.channels.clear();
        store.reads = 0;

        gateway.poll(session.id(), session.userId(), 0L);

        assertThat(bus.guilds).containsExactly(guild.id());
        assertThat(bus.channels).containsExactlyInAnyOrderElementsOf(channels.stream().map(Channel::id).toList());
        assertThat(store.reads).isEqualTo(1);
    }

    private List<Channel> channelsWithMembers(int members, int channels) {
        for (int i = 0; i < members; i++) {
            UUID member = UUID.randomUUID();
            seed.addMember(guild.id(), member);
            users.add(member);
        }
        List<Channel> created = new ArrayList<>();
        for (int i = 0; i < channels; i++) {
            created.add(seed.createChannel(guild.id(), "channel-" + i, ChannelType.GUILD_TEXT, null));
        }
        return created;
    }

    private static final class CountingStore implements GuildStore {
        private final GuildService seed;
        private int reads;

        private CountingStore(GuildService seed) {
            this.seed = seed;
        }

        @Override
        public Guild get(UUID guildId) {
            reads++;
            return seed.guild(guildId);
        }

        @Override
        public void create(Guild guild) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T update(UUID guildId, Function<Guild, T> mutation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UUID> guildIdsForMember(UUID memberId) {
            return seed.guildIdsForMember(memberId);
        }

        @Override
        public UUID guildIdForChannel(UUID channelId) {
            return seed.guildIdForChannel(channelId);
        }
    }

    private static final class SubscriptionRecordingBus implements GatewayEventBus {
        private final InMemoryGatewayEventBus delegate;
        private final Set<UUID> guilds = new HashSet<>();
        private final Set<UUID> channels = new HashSet<>();

        private SubscriptionRecordingBus(Clock clock) {
            this.delegate = new InMemoryGatewayEventBus(clock);
        }

        @Override
        public GatewayBusEvent publish(GatewayBusPublishCommand command) {
            return delegate.publish(command);
        }

        @Override
        public void addEventListener(Consumer<GatewayBusEvent> listener) {
            delegate.addEventListener(listener);
        }

        @Override
        public boolean tracksSubscriptions() {
            return true;
        }

        @Override
        public void subscribeGuild(UUID guildId) {
            guilds.add(guildId);
        }

        @Override
        public void subscribeChannel(UUID channelId) {
            channels.add(channelId);
        }
    }
}
