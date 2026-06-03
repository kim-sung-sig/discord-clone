package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
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
class PostgresMessageServiceTest {
    @Autowired
    private MessageSnapshotStore snapshots;

    @Autowired
    private DataSource dataSource;

    private UUID ownerId;
    private UUID guildId;
    private UUID channelId;

    @BeforeEach
    void cleanAndSeed() throws Exception {
        ownerId = UUID.randomUUID();
        guildId = UUID.randomUUID();
        channelId = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM message_mention_tokens");
            statement.executeUpdate("DELETE FROM message_mentions");
            statement.executeUpdate("DELETE FROM message_edits");
            statement.executeUpdate("DELETE FROM messages");
            statement.executeUpdate("DELETE FROM channel_role_overwrites");
            statement.executeUpdate("DELETE FROM guild_member_roles");
            statement.executeUpdate("DELETE FROM channels");
            statement.executeUpdate("DELETE FROM guild_roles");
            statement.executeUpdate("DELETE FROM guild_members");
            statement.executeUpdate("DELETE FROM guilds");
            statement.executeUpdate("DELETE FROM auth_accounts");
            statement.executeUpdate("DELETE FROM users");
        }
        insertUser(ownerId, "owner" + ownerId.toString().substring(0, 8));
        insertGuildAndChannel();
    }

    @Test
    void persistsMessagesAndReloadsEditsAndPins() {
        InMemoryMessageService service = new PersistentMessageService(snapshots);
        Message created = service.create(new CreateMessageCommand(guildId, channelId, ownerId, "hello @alice"));
        service.edit(new EditMessageCommand(guildId, channelId, created.id(), "hello <@" + ownerId + ">"));
        service.pin(guildId, channelId, created.id());

        InMemoryMessageService reloaded = new PersistentMessageService(snapshots);
        Message message = reloaded.message(guildId, channelId, created.id());

        assertThat(message.content()).isEqualTo(new MessageContent("hello <@" + ownerId + ">"));
        assertThat(message.mentions()).isEmpty();
        assertThat(message.pinned()).isTrue();
        assertThat(message.edited()).isTrue();
        assertThat(message.editHistory()).hasSize(1);
        assertThat(reloaded.messages(guildId, channelId, null, 10).messages())
            .extracting(Message::id)
            .containsExactly(created.id());
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

    private void insertGuildAndChannel() throws Exception {
        try (var connection = dataSource.getConnection();
             var guild = connection.prepareStatement("INSERT INTO guilds(id, name, owner_id) VALUES (?, ?, ?)");
             var channel = connection.prepareStatement("INSERT INTO channels(id, guild_id, name, type) VALUES (?, ?, ?, ?)")) {
            guild.setObject(1, guildId);
            guild.setString(2, "guild");
            guild.setObject(3, ownerId);
            guild.executeUpdate();
            channel.setObject(1, channelId);
            channel.setObject(2, guildId);
            channel.setString(3, "general");
            channel.setString(4, "GUILD_TEXT");
            channel.executeUpdate();
        }
    }
}
