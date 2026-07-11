package com.example.discord.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.discord.channel.ChannelType;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class PostgresServerEventServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STAGE_CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID HIDDEN_CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CREATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final Instant NOW = Instant.parse("2026-05-18T00:00:00Z");

    @Autowired
    private DataSource dataSource;

    private JdbcServerEventService service;

    @BeforeEach
    void cleanAndSeed() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM server_event_signals");
            statement.executeUpdate("DELETE FROM server_event_interested_members");
            statement.executeUpdate("DELETE FROM server_events");
            statement.executeUpdate("DELETE FROM notification_items");
            statement.executeUpdate("DELETE FROM notification_preferences");
            statement.executeUpdate("DELETE FROM premium_entitlements");
            statement.executeUpdate("DELETE FROM channel_role_overwrites");
            statement.executeUpdate("DELETE FROM guild_member_roles");
            statement.executeUpdate("DELETE FROM channels");
            statement.executeUpdate("DELETE FROM guild_roles");
            statement.executeUpdate("DELETE FROM guild_members");
            statement.executeUpdate("DELETE FROM guilds");
            statement.executeUpdate("DELETE FROM auth_accounts");
            statement.executeUpdate("DELETE FROM users");
        }
        insertUser(CREATOR_ID, "creator");
        insertUser(MEMBER_ID, "member");
        insertGuildAndChannels();
        service = new JdbcServerEventService(dataSource, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsServerEventAndMemberCanRsvpOnce() {
        ServerEvent event = service.createEvent(command(STAGE_CHANNEL_ID), true);

        ServerEvent rsvp = service.rsvpInterested(GUILD_ID, event.id(), MEMBER_ID);
        ServerEvent duplicate = service.rsvpInterested(GUILD_ID, event.id(), MEMBER_ID);

        assertThat(rsvp.interestedMemberIds()).containsExactly(MEMBER_ID);
        assertThat(duplicate.interestedMemberIds()).containsExactly(MEMBER_ID);
        assertThat(service.signals()).extracting(ServerEventSignal::type)
            .containsExactly(ServerEventSignalType.EVENT_RSVP_UPDATED, ServerEventSignalType.EVENT_CREATED);
    }

    @Test
    void visibleEventsExcludeHiddenChannelEvents() {
        ServerEvent visible = service.createEvent(command(STAGE_CHANNEL_ID), true);
        service.createEvent(command(HIDDEN_CHANNEL_ID), true);

        assertThat(service.visibleEvents(GUILD_ID, Set.of(STAGE_CHANNEL_ID)))
            .extracting(ServerEvent::id)
            .containsExactly(visible.id());
    }

    @Test
    void cancelEventRecordsSignalAndTerminalState() {
        ServerEvent event = service.createEvent(command(STAGE_CHANNEL_ID), true);

        ServerEvent canceled = service.cancelEvent(GUILD_ID, event.id(), CREATOR_ID, "speaker unavailable");

        assertThat(canceled.status()).isEqualTo(ServerEventStatus.CANCELED);
        assertThat(service.signals()).extracting(ServerEventSignal::type)
            .containsExactly(ServerEventSignalType.EVENT_CANCELED, ServerEventSignalType.EVENT_CREATED);
    }

    @Test
    void rejectsCreateWithoutPermissionOrInvalidTimeRange() {
        assertThatThrownBy(() -> service.createEvent(command(STAGE_CHANNEL_ID), false))
            .isInstanceOf(SecurityException.class)
            .hasMessage("server event management permission is required");
        assertThatThrownBy(() -> service.createEvent(new CreateServerEventCommand(
            GUILD_ID,
            STAGE_CHANNEL_ID,
            CREATOR_ID,
            "bad time",
            Instant.parse("2026-05-18T11:00:00Z"),
            Instant.parse("2026-05-18T10:00:00Z")
        ), true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("event end must be after start");
    }

    private static CreateServerEventCommand command(UUID channelId) {
        return new CreateServerEventCommand(
            GUILD_ID,
            channelId,
            CREATOR_ID,
            "Weekly stage",
            Instant.parse("2026-05-18T10:00:00Z"),
            Instant.parse("2026-05-18T11:00:00Z")
        );
    }

    private void insertUser(UUID id, String username) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO users(id, username, display_name, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?)
                 """)) {
            Timestamp createdAt = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));
            statement.setObject(1, id);
            statement.setString(2, username);
            statement.setString(3, username);
            statement.setTimestamp(4, createdAt);
            statement.setTimestamp(5, createdAt);
            statement.executeUpdate();
        }
    }

    private void insertGuildAndChannels() throws Exception {
        try (var connection = dataSource.getConnection();
             var guild = connection.prepareStatement("INSERT INTO guilds(id, name, owner_id) VALUES (?, ?, ?)");
             var channel = connection.prepareStatement("INSERT INTO channels(id, guild_id, name, type) VALUES (?, ?, ?, ?)")) {
            guild.setObject(1, GUILD_ID);
            guild.setString(2, "events");
            guild.setObject(3, CREATOR_ID);
            guild.executeUpdate();
            insertChannel(channel, STAGE_CHANNEL_ID, "stage");
            insertChannel(channel, HIDDEN_CHANNEL_ID, "hidden");
        }
    }

    private static void insertChannel(java.sql.PreparedStatement statement, UUID channelId, String name) throws Exception {
        statement.setObject(1, channelId);
        statement.setObject(2, GUILD_ID);
        statement.setString(3, name);
        statement.setString(4, ChannelType.GUILD_VOICE.name());
        statement.executeUpdate();
    }
}
