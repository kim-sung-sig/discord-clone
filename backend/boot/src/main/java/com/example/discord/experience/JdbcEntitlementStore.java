package com.example.discord.experience;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
class JdbcEntitlementStore implements EntitlementStore {
    private final DataSource dataSource;

    JdbcEntitlementStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Entitlement save(Entitlement entitlement) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO premium_entitlements(
                     id, user_id, guild_id, feature_key, status, provider, provider_subscription_id, granted_at, expires_at
                 )
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                 ON CONFLICT (id) DO UPDATE
                 SET status = EXCLUDED.status,
                     expires_at = EXCLUDED.expires_at,
                     updated_at = NOW()
                 """)) {
            statement.setObject(1, entitlement.id());
            statement.setObject(2, entitlement.userId());
            statement.setObject(3, entitlement.guildId());
            statement.setString(4, entitlement.featureKey());
            statement.setString(5, entitlement.status().name());
            statement.setString(6, entitlement.provider());
            statement.setString(7, entitlement.providerSubscriptionId());
            statement.setTimestamp(8, Timestamp.from(entitlement.grantedAt()));
            setInstant(statement, 9, entitlement.expiresAt());
            statement.executeUpdate();
            return entitlement;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save premium entitlement", exception);
        }
    }

    @Override
    public Optional<Entitlement> findById(UUID id) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, user_id, guild_id, feature_key, status, provider, provider_subscription_id, granted_at, expires_at
                 FROM premium_entitlements
                 WHERE id = ?
                 """)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(entitlement(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find premium entitlement", exception);
        }
    }

    @Override
    public Optional<Entitlement> findByProviderSubscription(String provider, String providerSubscriptionId) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, user_id, guild_id, feature_key, status, provider, provider_subscription_id, granted_at, expires_at
                 FROM premium_entitlements
                 WHERE provider = ?
                   AND provider_subscription_id = ?
                 """)) {
            statement.setString(1, provider);
            statement.setString(2, providerSubscriptionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(entitlement(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find premium entitlement by provider subscription", exception);
        }
    }

    @Override
    public List<Entitlement> findByUserAndFeature(UUID userId, String featureKey) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, user_id, guild_id, feature_key, status, provider, provider_subscription_id, granted_at, expires_at
                 FROM premium_entitlements
                 WHERE user_id = ?
                   AND feature_key = ?
                 ORDER BY granted_at, id
                 """)) {
            statement.setObject(1, userId);
            statement.setString(2, featureKey);
            return entitlements(statement.executeQuery());
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list premium entitlements by user and feature", exception);
        }
    }

    @Override
    public List<Entitlement> findByGuild(UUID guildId) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, user_id, guild_id, feature_key, status, provider, provider_subscription_id, granted_at, expires_at
                 FROM premium_entitlements
                 WHERE guild_id = ?
                 ORDER BY granted_at, id
                 """)) {
            statement.setObject(1, guildId);
            return entitlements(statement.executeQuery());
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list premium entitlements by guild", exception);
        }
    }

    private static List<Entitlement> entitlements(ResultSet resultSet) throws SQLException {
        try (resultSet) {
            List<Entitlement> entitlements = new ArrayList<>();
            while (resultSet.next()) {
                entitlements.add(entitlement(resultSet));
            }
            return List.copyOf(entitlements);
        }
    }

    private static Entitlement entitlement(ResultSet resultSet) throws SQLException {
        Timestamp expiresAt = resultSet.getTimestamp("expires_at");
        return new Entitlement(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("user_id", UUID.class),
            resultSet.getObject("guild_id", UUID.class),
            resultSet.getString("feature_key"),
            EntitlementStatus.valueOf(resultSet.getString("status")),
            resultSet.getString("provider"),
            resultSet.getString("provider_subscription_id"),
            resultSet.getTimestamp("granted_at").toInstant(),
            expiresAt == null ? null : expiresAt.toInstant()
        );
    }

    private static void setInstant(java.sql.PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setTimestamp(index, null);
            return;
        }
        statement.setTimestamp(index, Timestamp.from(value));
    }
}
