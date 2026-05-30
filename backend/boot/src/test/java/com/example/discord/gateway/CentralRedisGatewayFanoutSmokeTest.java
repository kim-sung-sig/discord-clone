package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.channel.ChannelType;
import com.example.discord.guild.Channel;
import com.example.discord.guild.Guild;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_CENTRAL_REDIS_GATEWAY_SMOKE", matches = "true")
class CentralRedisGatewayFanoutSmokeTest {
    @Test
    void twoGatewayNodesBothReceiveSameStreamEventThroughCentralRedis() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            UUID guildId = UUID.randomUUID();
            UUID channelId = UUID.randomUUID();
            String stream = "gateway:channel:" + channelId;
            redis.delete(stream);

            RedisGatewayEventBus publisher = eventBus(redis, "publisher");
            RedisGatewayEventBus nodeA = eventBus(redis, "node-a");
            RedisGatewayEventBus nodeB = eventBus(redis, "node-b");
            CopyOnWriteArrayList<GatewayBusEvent> receivedByNodeA = new CopyOnWriteArrayList<>();
            CopyOnWriteArrayList<GatewayBusEvent> receivedByNodeB = new CopyOnWriteArrayList<>();
            nodeA.addEventListener(receivedByNodeA::add);
            nodeB.addEventListener(receivedByNodeB::add);
            nodeA.subscribeChannel(channelId);
            nodeB.subscribeChannel(channelId);

            GatewayBusEvent published = publisher.publish(new GatewayBusPublishCommand(
                "MESSAGE_CREATE",
                guildId,
                channelId,
                Map.of("content", "central redis fanout")
            ));

            pollUntilBothReceive(nodeA, nodeB, receivedByNodeA, receivedByNodeB);

            assertThat(receivedByNodeA).extracting(GatewayBusEvent::eventId).containsExactly(published.eventId());
            assertThat(receivedByNodeB).extracting(GatewayBusEvent::eventId).containsExactly(published.eventId());
            assertThat(receivedByNodeA.getFirst().payload()).containsEntry("content", "central redis fanout");
            assertThat(receivedByNodeB.getFirst().payload()).containsEntry("content", "central redis fanout");
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void gatewayServicesDeliverOnlyVisibleChannelEventsThroughCentralRedis() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            RedisGatewayEventBus nodeABus = eventBus(redis, "service-node-a");
            RedisGatewayEventBus nodeBBus = eventBus(redis, "service-node-b");
            InMemoryGuildService guildService = new InMemoryGuildService();
            InMemoryGatewaySessionRegistry sessionRegistry = new InMemoryGatewaySessionRegistry();
            InMemoryGatewayService nodeA = gatewayService(guildService, nodeABus, sessionRegistry);
            InMemoryGatewayService nodeB = gatewayService(guildService, nodeBBus, sessionRegistry);
            UUID ownerId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            Guild guild = guildService.createGuild("Discord Clone", ownerId);
            Channel visible = guildService.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
            Channel hidden = guildService.createChannel(guild.id(), "staff", ChannelType.GUILD_TEXT, null);
            guildService.addMember(guild.id(), memberId);
            denyEveryoneView(guildService, guild, hidden);
            GatewayIdentifyResult identifiedOnNodeB = nodeB.identify(memberId);

            GatewayEvent visibleEvent = nodeA.publish(
                "MESSAGE_CREATE",
                guild.id(),
                visible.id(),
                Map.of("content", "visible through redis")
            );
            nodeA.publish("MESSAGE_CREATE", guild.id(), hidden.id(), Map.of("content", "hidden through redis"));
            nodeBBus.pollOnce();

            List<GatewayEvent> deliveredOnNodeB = nodeB.poll(identifiedOnNodeB.session().id(), memberId, 0L);

            assertThat(deliveredOnNodeB).extracting(GatewayEvent::busEventId).containsExactly(visibleEvent.busEventId());
            assertThat(deliveredOnNodeB)
                .extracting(event -> event.payload().get("content"))
                .containsExactly("visible through redis");
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void resumedSessionOnDifferentNodeSubscribesBeforeRedisReplay() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            RedisGatewayEventBus nodeABus = eventBus(redis, "resume-node-a");
            RedisGatewayEventBus nodeBBus = eventBus(redis, "resume-node-b");
            InMemoryGuildService guildService = new InMemoryGuildService();
            InMemoryGatewaySessionRegistry sessionRegistry = new InMemoryGatewaySessionRegistry();
            InMemoryGatewayService nodeA = gatewayService(guildService, nodeABus, sessionRegistry);
            InMemoryGatewayService nodeB = gatewayService(guildService, nodeBBus, sessionRegistry);
            UUID ownerId = UUID.randomUUID();
            Guild guild = guildService.createGuild("Discord Clone", ownerId);
            Channel channel = guildService.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
            GatewayIdentifyResult identifiedOnNodeA = nodeA.identify(ownerId);

            nodeB.resume(identifiedOnNodeA.session().id(), ownerId, 0L);
            GatewayEvent publishedAfterResume = nodeA.publish(
                "MESSAGE_CREATE",
                guild.id(),
                channel.id(),
                Map.of("content", "received after redis resume")
            );
            nodeBBus.pollOnce();

            List<GatewayEvent> deliveredOnNodeB = nodeB.poll(
                identifiedOnNodeA.session().id(),
                ownerId,
                identifiedOnNodeA.ready().sequence()
            );

            assertThat(deliveredOnNodeB).extracting(GatewayEvent::busEventId)
                .containsExactly(publishedAfterResume.busEventId());
            assertThat(deliveredOnNodeB)
                .extracting(event -> event.payload().get("content"))
                .containsExactly("received after redis resume");
        } finally {
            connectionFactory.destroy();
        }
    }

    private static RedisGatewayEventBus eventBus(StringRedisTemplate redis, String nodeId) {
        return new RedisGatewayEventBus(
            redis,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneOffset.UTC),
            nodeId,
            "discord-gateway-smoke",
            1000L
        );
    }

    private static InMemoryGatewayService gatewayService(
        InMemoryGuildService guildService,
        RedisGatewayEventBus eventBus,
        InMemoryGatewaySessionRegistry sessionRegistry
    ) {
        return new InMemoryGatewayService(
            guildService,
            Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofSeconds(30),
            eventBus,
            sessionRegistry
        );
    }

    private static void pollUntilBothReceive(
        RedisGatewayEventBus nodeA,
        RedisGatewayEventBus nodeB,
        CopyOnWriteArrayList<GatewayBusEvent> receivedByNodeA,
        CopyOnWriteArrayList<GatewayBusEvent> receivedByNodeB
    ) {
        Instant deadline = Instant.now().plusSeconds(10);
        while ((receivedByNodeA.isEmpty() || receivedByNodeB.isEmpty()) && Instant.now().isBefore(deadline)) {
            nodeA.pollOnce();
            nodeB.pollOnce();
        }
    }

    private static LettuceConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
            envOrDefault("SPRING_DATA_REDIS_HOST", "127.0.0.1"),
            Integer.parseInt(envOrDefault("SPRING_DATA_REDIS_PORT", "16379"))
        );
        configuration.setPassword(RedisPassword.of(envOrDefault("SPRING_DATA_REDIS_PASSWORD", "dev_password")));
        return new LettuceConnectionFactory(configuration);
    }

    private static void denyEveryoneView(InMemoryGuildService guildService, Guild guild, Channel channel) {
        guildService.addChannelRoleOverwrite(
            guild.id(),
            channel.id(),
            guild.everyoneRole().id(),
            PermissionSet.empty(),
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
        );
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
