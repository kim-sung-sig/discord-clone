package com.example.discord.event;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
class JdbcServerEventService {
    private final DataSource dataSource;
    private final Clock clock;
    private final Function<ServerEvent, Set<UUID>> recipients;
    private Instant lastSignalCreatedAt = Instant.EPOCH;

    @Autowired
    JdbcServerEventService(DataSource dataSource, Clock clock, Function<ServerEvent, Set<UUID>> recipients) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.recipients = Objects.requireNonNull(recipients, "recipients must not be null");
    }

    ServerEvent createEvent(CreateServerEventCommand command, boolean canManageEvents) {
        if (!canManageEvents) {
            throw new SecurityException("server event management permission is required");
        }
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ServerEvent event = new ServerEvent(
                    eventId,
                    command.guildId(),
                    command.channelId(),
                    command.creatorId(),
                    command.title(),
                    command.startsAt(),
                    command.endsAt(),
                    ServerEventStatus.SCHEDULED,
                    List.of(),
                    now
                );
                saveEvent(connection, event);
                UUID signalId = appendSignal(connection, event.guildId(), event.id(), event.creatorId(), ServerEventSignalType.EVENT_CREATED, "event created");
                notifyRecipients(connection, event, event.creatorId(), signalId, "Server event created");
                connection.commit();
                return event;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to create server event", exception);
        }
    }

    ServerEvent rsvpInterested(UUID guildId, UUID eventId, UUID memberId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ServerEvent current = requireEvent(connection, guildId, eventId);
                if (current.status() == ServerEventStatus.CANCELED) {
                    throw new IllegalArgumentException("event already canceled");
                }
                boolean changed = insertInterestedMember(connection, eventId, memberId);
                if (changed) {
                    appendSignal(connection, guildId, eventId, memberId, ServerEventSignalType.EVENT_RSVP_UPDATED, "event rsvp updated");
                }
                ServerEvent updated = requireEvent(connection, guildId, eventId);
                connection.commit();
                return updated;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to RSVP server event", exception);
        }
    }

    ServerEvent cancelEvent(UUID guildId, UUID eventId, UUID actorId, String reason) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ServerEvent current = requireEvent(connection, guildId, eventId);
                if (current.status() == ServerEventStatus.CANCELED) {
                    throw new IllegalArgumentException("event already canceled");
                }
                ServerEvent canceled = new ServerEvent(
                    current.id(),
                    current.guildId(),
                    current.channelId(),
                    current.creatorId(),
                    current.title(),
                    current.startsAt(),
                    current.endsAt(),
                    ServerEventStatus.CANCELED,
                    current.interestedMemberIds(),
                    clock.instant()
                );
                updateStatus(connection, canceled);
                UUID signalId = appendSignal(connection, guildId, eventId, actorId, ServerEventSignalType.EVENT_CANCELED, reason);
                notifyRecipients(connection, canceled, actorId, signalId, "Server event canceled");
                connection.commit();
                return canceled;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to cancel server event", exception);
        }
    }

    List<ServerEvent> visibleEvents(UUID guildId, Set<UUID> visibleChannelIds) {
        Set<UUID> visible = visibleChannelIds == null ? Set.of() : Set.copyOf(visibleChannelIds);
        if (visible.isEmpty()) {
            return List.of();
        }
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, guild_id, channel_id, creator_id, title, starts_at, ends_at, status, updated_at
                 FROM server_events
                 WHERE guild_id = ?
                   AND channel_id = ANY(?)
                 ORDER BY starts_at, id LIMIT 100
                 """)) {
            statement.setObject(1, guildId);
            statement.setArray(2, connection.createArrayOf("uuid", visible.toArray()));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ServerEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    events.add(event(connection, resultSet));
                }
                return List.copyOf(events);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list visible server events", exception);
        }
    }

    ServerEvent event(UUID guildId, UUID eventId) {
        try (Connection connection = dataSource.getConnection()) {
            return requireEvent(connection, guildId, eventId);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to load server event", exception);
        }
    }

    List<ServerEventSignal> signals() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, guild_id, event_id, actor_id, type, detail, created_at
                 FROM server_event_signals
                 ORDER BY created_at DESC, id
                 """);
             ResultSet resultSet = statement.executeQuery()) {
            List<ServerEventSignal> signals = new ArrayList<>();
            while (resultSet.next()) {
                signals.add(new ServerEventSignal(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("guild_id", UUID.class),
                    resultSet.getObject("event_id", UUID.class),
                    resultSet.getObject("actor_id", UUID.class),
                    ServerEventSignalType.valueOf(resultSet.getString("type")),
                    resultSet.getString("detail"),
                    resultSet.getTimestamp("created_at").toInstant()
                ));
            }
            return List.copyOf(signals);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list server event signals", exception);
        }
    }

    private void saveEvent(Connection connection, ServerEvent event) throws SQLException {
        try (var statement = connection.prepareStatement("""
             INSERT INTO server_events(id, guild_id, channel_id, creator_id, title, starts_at, ends_at, status, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
            statement.setObject(1, event.id());
            statement.setObject(2, event.guildId());
            statement.setObject(3, event.channelId());
            statement.setObject(4, event.creatorId());
            statement.setString(5, event.title());
            statement.setTimestamp(6, Timestamp.from(event.startsAt()));
            statement.setTimestamp(7, Timestamp.from(event.endsAt()));
            statement.setString(8, event.status().name());
            statement.setTimestamp(9, Timestamp.from(event.updatedAt()));
            statement.executeUpdate();
        }
    }

    private void updateStatus(Connection connection, ServerEvent event) throws SQLException {
        try (var statement = connection.prepareStatement("""
             UPDATE server_events
             SET status = ?,
                 updated_at = ?
             WHERE id = ?
             """)) {
            statement.setString(1, event.status().name());
            statement.setTimestamp(2, Timestamp.from(event.updatedAt()));
            statement.setObject(3, event.id());
            statement.executeUpdate();
        }
    }

    private boolean insertInterestedMember(Connection connection, UUID eventId, UUID memberId) throws SQLException {
        try (var statement = connection.prepareStatement("""
             INSERT INTO server_event_interested_members(event_id, member_id)
             VALUES (?, ?)
             ON CONFLICT DO NOTHING
             """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, memberId);
            return statement.executeUpdate() == 1;
        }
    }

    private ServerEvent requireEvent(Connection connection, UUID guildId, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement("""
             SELECT id, guild_id, channel_id, creator_id, title, starts_at, ends_at, status, updated_at
             FROM server_events
             WHERE id = ?
               AND guild_id = ?
             """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("event not found");
                }
                return event(connection, resultSet);
            }
        }
    }

    private ServerEvent event(Connection connection, ResultSet resultSet) throws SQLException {
        UUID eventId = resultSet.getObject("id", UUID.class);
        return new ServerEvent(
            eventId,
            resultSet.getObject("guild_id", UUID.class),
            resultSet.getObject("channel_id", UUID.class),
            resultSet.getObject("creator_id", UUID.class),
            resultSet.getString("title"),
            resultSet.getTimestamp("starts_at").toInstant(),
            resultSet.getTimestamp("ends_at").toInstant(),
            ServerEventStatus.valueOf(resultSet.getString("status")),
            interestedMemberIds(connection, eventId),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private List<UUID> interestedMemberIds(Connection connection, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement("""
             SELECT member_id
             FROM server_event_interested_members
             WHERE event_id = ?
             ORDER BY joined_at, member_id
             """)) {
            statement.setObject(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<UUID> memberIds = new ArrayList<>();
                while (resultSet.next()) {
                    memberIds.add(resultSet.getObject("member_id", UUID.class));
                }
                return List.copyOf(memberIds);
            }
        }
    }

    private UUID appendSignal(
        Connection connection,
        UUID guildId,
        UUID eventId,
        UUID actorId,
        ServerEventSignalType type,
        String detail
    ) throws SQLException {
        UUID signalId = UUID.randomUUID();
        try (var statement = connection.prepareStatement("""
             INSERT INTO server_event_signals(id, guild_id, event_id, actor_id, type, detail, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?)
             """)) {
            statement.setObject(1, signalId);
            statement.setObject(2, guildId);
            statement.setObject(3, eventId);
            statement.setObject(4, actorId);
            statement.setString(5, type.name());
            statement.setString(6, detail == null ? "" : detail);
            statement.setTimestamp(7, Timestamp.from(nextSignalInstant()));
            statement.executeUpdate();
        }
        return signalId;
    }

    private void notifyRecipients(Connection connection, ServerEvent event, UUID actorId, UUID signalId, String summary)
        throws SQLException {
        Set<UUID> recipientIds = recipients.apply(event);
        for (UUID userId : recipientIds) {
            if (userId.equals(actorId)) {
                continue;
            }
            try (var statement = connection.prepareStatement("""
                INSERT INTO notification_items(
                    id, user_id, guild_id, channel_id, source_id, sequence, kind, summary, read, created_at
                )
                SELECT ?, ?, ?, ?, ?, 0, 'SERVER', ?, FALSE, NOW()
                WHERE COALESCE((
                    SELECT server_notifications_enabled FROM notification_preferences WHERE user_id = ?
                ), TRUE)
                ON CONFLICT (user_id, source_id, kind) DO NOTHING
                """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, userId);
                statement.setObject(3, event.guildId());
                statement.setObject(4, event.channelId());
                statement.setObject(5, signalId);
                statement.setString(6, summary);
                statement.setObject(7, userId);
                statement.executeUpdate();
            }
        }
    }

    private Instant nextSignalInstant() {
        Instant now = clock.instant();
        if (!now.isAfter(lastSignalCreatedAt)) {
            now = lastSignalCreatedAt.plusMillis(1);
        }
        lastSignalCreatedAt = now;
        return now;
    }
}
