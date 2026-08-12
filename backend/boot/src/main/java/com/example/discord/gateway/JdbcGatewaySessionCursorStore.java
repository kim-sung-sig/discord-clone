package com.example.discord.gateway;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
@DependsOn("postgresFlyway")
class JdbcGatewaySessionCursorStore implements GatewaySessionCursorStore {
    private final DataSource dataSource;
    private final Clock clock;

    JdbcGatewaySessionCursorStore(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void create(GatewaySessionCursor cursor) {
        Objects.requireNonNull(cursor, "cursor must not be null");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO gateway_session_delivery(
                     session_id, user_id, acknowledged_user_sequence, highest_granted_user_sequence,
                     highest_delivered_user_sequence, delivery_epoch, owner_instance_id,
                     owner_lease_expires_at, version, updated_at, expires_at
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                 ON CONFLICT (session_id, user_id) DO NOTHING
                 """)) {
            bindCursor(statement, cursor);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to create gateway session cursor", exception);
        }
    }

    @Override
    public Optional<GatewaySessionCursor> find(UUID sessionId, UUID userId) {
        try (Connection connection = dataSource.getConnection()) {
            return Optional.ofNullable(find(connection, sessionId, userId, false));
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find gateway session cursor", exception);
        }
    }

    @Override
    public GatewaySessionCursor markDelivered(
        UUID sessionId,
        UUID userId,
        long deliveryEpoch,
        String ownerInstanceId,
        long sequence,
        Instant now
    ) {
        return update(sessionId, userId, deliveryEpoch, ownerInstanceId, now,
            cursor -> cursor.grantAndDeliver(sequence, now));
    }

    @Override
    public GatewaySessionCursor acknowledge(
        UUID sessionId,
        UUID userId,
        long deliveryEpoch,
        String ownerInstanceId,
        long sequence
    ) {
        return update(sessionId, userId, deliveryEpoch, ownerInstanceId, clock.instant(),
            cursor -> cursor.acknowledge(sequence));
    }

    @Override
    public GatewaySessionCursor replaceEpoch(
        UUID sessionId,
        UUID userId,
        String ownerInstanceId,
        Instant leaseExpiresAt,
        Instant now
    ) {
        GatewaySessionCursor current = find(sessionId, userId)
            .orElseThrow(() -> new GatewaySessionNotFoundException("gateway session cursor not found"));
        return update(sessionId, userId, current.deliveryEpoch(), current.ownerInstanceId(), now,
            cursor -> cursor.nextEpoch(ownerInstanceId, leaseExpiresAt, now));
    }

    private GatewaySessionCursor update(
        UUID sessionId,
        UUID userId,
        long deliveryEpoch,
        String ownerInstanceId,
        Instant now,
        java.util.function.UnaryOperator<GatewaySessionCursor> operation
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                GatewaySessionCursor current = find(connection, sessionId, userId, true);
                if (current == null) {
                    throw new GatewaySessionNotFoundException("gateway session cursor not found");
                }
                if (current.deliveryEpoch() != deliveryEpoch) {
                    throw new GatewayStaleDeliveryEpochException("gateway delivery epoch is stale");
                }
                if (!current.ownerInstanceId().equals(ownerInstanceId)) {
                    throw new GatewayDeliveryOwnerMismatchException("gateway delivery owner mismatch");
                }
                GatewaySessionCursor updated = operation.apply(current);
                try (var statement = connection.prepareStatement("""
                    UPDATE gateway_session_delivery
                    SET acknowledged_user_sequence = ?, highest_granted_user_sequence = ?,
                        highest_delivered_user_sequence = ?, delivery_epoch = ?, owner_instance_id = ?,
                        owner_lease_expires_at = ?, version = version + 1, updated_at = ?, expires_at = ?
                    WHERE session_id = ? AND user_id = ? AND version = ?
                    """)) {
                    statement.setLong(1, updated.acknowledgedUserSequence());
                    statement.setLong(2, updated.highestGrantedUserSequence());
                    statement.setLong(3, updated.highestDeliveredUserSequence());
                    statement.setLong(4, updated.deliveryEpoch());
                    statement.setString(5, updated.ownerInstanceId());
                    statement.setTimestamp(6, Timestamp.from(updated.ownerLeaseExpiresAt()));
                    statement.setTimestamp(7, Timestamp.from(now));
                    statement.setTimestamp(8, Timestamp.from(updated.ownerLeaseExpiresAt()));
                    statement.setObject(9, sessionId);
                    statement.setObject(10, userId);
                    statement.setLong(11, currentVersion(connection, sessionId, userId));
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("gateway cursor compare-and-set failed");
                    }
                }
                connection.commit();
                return updated;
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("failed to update gateway session cursor", exception);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update gateway session cursor", exception);
        }
    }

    private GatewaySessionCursor find(Connection connection, UUID sessionId, UUID userId, boolean forUpdate)
        throws SQLException {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        try (var statement = connection.prepareStatement("""
            SELECT session_id, user_id, acknowledged_user_sequence, highest_granted_user_sequence,
                   highest_delivered_user_sequence, delivery_epoch, owner_instance_id,
                   owner_lease_expires_at, updated_at
            FROM gateway_session_delivery
            WHERE session_id = ? AND user_id = ?
            """ + suffix)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new GatewaySessionCursor(
                    resultSet.getObject("session_id", UUID.class),
                    resultSet.getObject("user_id", UUID.class),
                    resultSet.getLong("acknowledged_user_sequence"),
                    resultSet.getLong("highest_granted_user_sequence"),
                    resultSet.getLong("highest_delivered_user_sequence"),
                    resultSet.getLong("delivery_epoch"),
                    resultSet.getString("owner_instance_id"),
                    resultSet.getTimestamp("owner_lease_expires_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
                );
            }
        }
    }

    private long currentVersion(Connection connection, UUID sessionId, UUID userId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT version FROM gateway_session_delivery WHERE session_id = ? AND user_id = ?
            """)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new GatewaySessionNotFoundException("gateway session cursor not found");
                }
                return resultSet.getLong(1);
            }
        }
    }

    private static void bindCursor(java.sql.PreparedStatement statement, GatewaySessionCursor cursor) throws SQLException {
        statement.setObject(1, cursor.sessionId());
        statement.setObject(2, cursor.userId());
        statement.setLong(3, cursor.acknowledgedUserSequence());
        statement.setLong(4, cursor.highestGrantedUserSequence());
        statement.setLong(5, cursor.highestDeliveredUserSequence());
        statement.setLong(6, cursor.deliveryEpoch());
        statement.setString(7, cursor.ownerInstanceId());
        statement.setTimestamp(8, Timestamp.from(cursor.ownerLeaseExpiresAt()));
        statement.setTimestamp(9, Timestamp.from(cursor.updatedAt()));
        statement.setTimestamp(10, Timestamp.from(cursor.ownerLeaseExpiresAt()));
    }
}
