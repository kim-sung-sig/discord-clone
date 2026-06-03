package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
class JdbcMessageStoreTest {
    private static final Instant NOW = Instant.parse("2026-06-03T00:00:00Z");

    @Autowired
    private MessageStore messages;

    @Autowired
    private MessagePublicationStore publications;

    @Autowired
    private ChannelMessagePagePort pages;

    @Autowired
    private ChannelMessageSearchPort search;

    @Autowired
    private MessageLookupPort lookup;

    @Autowired
    private MessagePublicationOutbox outbox;

    @Autowired
    private MessagePublicationOutboxQueue outboxQueue;

    @Autowired
    private MessagePublicationDeadLetterQueue deadLetters;

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
            statement.executeUpdate("DELETE FROM message_publication_outbox_mentions");
            statement.executeUpdate("DELETE FROM message_publication_outbox");
            statement.executeUpdate("DELETE FROM message_idempotency_keys");
            statement.executeUpdate("DELETE FROM message_mention_targets");
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
    void postgresProfileUsesJdbcMessagePorts() {
        assertThat(messages).isInstanceOf(JdbcMessageStore.class);
        assertThat(publications).isSameAs(messages);
        assertThat(pages).isSameAs(messages);
        assertThat(search).isSameAs(messages);
        assertThat(lookup).isSameAs(messages);
        assertThat(outbox).isSameAs(messages);
        assertThat(outboxQueue).isSameAs(messages);
        assertThat(deadLetters).isSameAs(messages);
    }

    @Test
    void savePublishedPersistsMessageIdempotencyKeyAndOutboxInOnePortCall() throws Exception {
        Message message = message("published once", List.of(new SpecialMentionTarget(SpecialMentionKind.EVERYONE)));
        IdempotencyKey idempotencyKey = new IdempotencyKey("send-" + UUID.randomUUID());
        MessagePublished event = new MessagePublished(
            UUID.randomUUID(),
            message.id(),
            message.author(),
            message.target(),
            message.mentions(),
            "correlation-save-published",
            NOW
        );

        publications.savePublished(message, idempotencyKey, event);

        assertThat(messages.findById(message.id())).contains(message);
        assertThat(messages.findByIdempotencyKey(message.author(), message.target(), idempotencyKey))
            .contains(message);
        assertThat(rowCount("message_publication_outbox")).isEqualTo(1);
        assertThat(rowCount("message_publication_outbox_mentions")).isEqualTo(1);
    }

    @Test
    void claimsMarksAndReleasesOutboxPublications() throws Exception {
        Message message = message("relay me", List.of());
        MessagePublished event = new MessagePublished(
            UUID.randomUUID(),
            message.id(),
            message.author(),
            message.target(),
            message.mentions(),
            "correlation-relay",
            NOW
        );
        publications.savePublished(message, new IdempotencyKey("send-" + UUID.randomUUID()), event);

        List<ClaimedMessagePublication> claimed = outboxQueue.claimPendingPublications(
            10,
            NOW,
            Duration.ofSeconds(30)
        );

        assertThat(claimed).singleElement().satisfies(publication -> {
            assertThat(publication.event()).isEqualTo(event);
            assertThat(publication.claimToken()).isNotNull();
        });
        outboxQueue.releaseFailed(event.eventId(), claimed.getFirst().claimToken(), "temporary failure", NOW);
        assertThat(outboxAttempts(event.eventId())).isEqualTo(1);

        ClaimedMessagePublication retried = outboxQueue.claimPendingPublications(10, NOW.plusSeconds(31), Duration.ofSeconds(30))
            .getFirst();
        outboxQueue.markPublished(event.eventId(), retried.claimToken(), NOW.plusSeconds(32));

        assertThat(unpublishedOutboxCount()).isZero();
        assertThat(publishedOutboxCount()).isEqualTo(1);
    }

    @Test
    void listsAndRequeuesDeadLetterPublications() throws Exception {
        Message message = message("dead letter replay", List.of(new SpecialMentionTarget(SpecialMentionKind.HERE)));
        MessagePublished event = new MessagePublished(
            UUID.randomUUID(),
            message.id(),
            message.author(),
            message.target(),
            message.mentions(),
            "correlation-dead-letter",
            NOW
        );
        publications.savePublished(message, new IdempotencyKey("send-" + UUID.randomUUID()), event);

        for (int attempt = 1; attempt <= 10; attempt++) {
            ClaimedMessagePublication claimed = outboxQueue.claimPendingPublications(
                    1,
                    NOW.plusSeconds(attempt * 31L),
                    Duration.ofSeconds(30)
                )
                .getFirst();
            outboxQueue.releaseFailed(
                event.eventId(),
                claimed.claimToken(),
                "gateway down " + attempt,
                NOW.plusSeconds(attempt * 31L)
            );
        }

        assertThat(deadLetters.listDeadLetters(10))
            .singleElement()
            .satisfies(deadLetter -> {
                assertThat(deadLetter.event()).isEqualTo(event);
                assertThat(deadLetter.attempts()).isEqualTo(10);
                assertThat(deadLetter.lastError()).isEqualTo("gateway down 10");
            });

        assertThat(deadLetters.requeueDeadLetter(event.eventId(), NOW.plusSeconds(600))).isTrue();
        assertThat(deadLetters.listDeadLetters(10)).isEmpty();
        assertThat(outboxQueue.claimPendingPublications(1, NOW.plusSeconds(601), Duration.ofSeconds(30)))
            .singleElement()
            .satisfies(publication -> assertThat(publication.event()).isEqualTo(event));
    }

    @Test
    void persistsMessageThroughStoreReadSearchLookupAndOutboxPorts() throws Exception {
        Message message = message(
            "hello searchable",
            List.of(
                new RoleMentionTarget(UUID.randomUUID()),
                new SpecialMentionTarget(SpecialMentionKind.HERE)
            )
        );
        IdempotencyKey idempotencyKey = new IdempotencyKey("send-" + UUID.randomUUID());

        messages.save(message, idempotencyKey);
        outbox.append(new MessagePublished(
            UUID.randomUUID(),
            message.id(),
            message.author(),
            message.target(),
            message.mentions(),
            "correlation-1",
            NOW
        ));

        assertThat(messages.findById(message.id())).contains(message);
        assertThat(messages.findByIdempotencyKey(message.author(), message.target(), idempotencyKey))
            .contains(message);
        assertThat(pages.read(target(), null, 10).messages())
            .extracting(Message::id)
            .containsExactly(message.id());
        assertThat(search.search(target(), "searchable", 10))
            .extracting(Message::id)
            .containsExactly(message.id());
        assertThat(lookup.requireMessage(target(), message.id())).isEqualTo(message);
        assertThat(rowCount("message_publication_outbox")).isEqualTo(1);
        assertThat(rowCount("message_publication_outbox_mentions")).isEqualTo(2);
    }

    @Test
    void ignoresExpiredIdempotencyKeys() throws Exception {
        Message message = message("ttl message", List.of());
        IdempotencyKey idempotencyKey = new IdempotencyKey("send-" + UUID.randomUUID());
        messages.save(message, idempotencyKey);

        expireIdempotencyKey(idempotencyKey);

        assertThat(messages.findByIdempotencyKey(message.author(), message.target(), idempotencyKey))
            .isEmpty();
    }

    private Message message(String content, List<MessageMentionTarget> mentions) {
        return new Message(
            UUID.randomUUID(),
            new UserMessageAuthor(ownerId),
            target(),
            new MessageContent(content),
            mentions,
            false,
            false,
            List.of(),
            NOW,
            NOW
        );
    }

    private ChannelMessageTarget target() {
        return new ChannelMessageTarget(guildId, channelId);
    }

    private void expireIdempotencyKey(IdempotencyKey idempotencyKey) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 UPDATE message_idempotency_keys
                 SET expires_at = ?
                 WHERE idempotency_key = ?
                 """)) {
            statement.setTimestamp(1, Timestamp.from(NOW.minusSeconds(1)));
            statement.setString(2, idempotencyKey.value());
            statement.executeUpdate();
        }
    }

    private int rowCount(String tableName) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) AS row_count FROM " + tableName)) {
            resultSet.next();
            return resultSet.getInt("row_count");
        }
    }

    private int unpublishedOutboxCount() throws Exception {
        return scalar("""
            SELECT COUNT(*) AS value
            FROM message_publication_outbox
            WHERE published_at IS NULL
              AND dead_lettered_at IS NULL
            """);
    }

    private int publishedOutboxCount() throws Exception {
        return scalar("""
            SELECT COUNT(*) AS value
            FROM message_publication_outbox
            WHERE published_at IS NOT NULL
            """);
    }

    private int outboxAttempts(UUID eventId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT attempts
                 FROM message_publication_outbox
                 WHERE event_id = ?
                 """)) {
            statement.setObject(1, eventId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt("attempts");
            }
        }
    }

    private int scalar(String sql) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt("value");
        }
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
