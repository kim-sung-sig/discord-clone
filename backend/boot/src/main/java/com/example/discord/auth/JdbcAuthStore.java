package com.example.discord.auth;

import com.example.discord.identity.EmailAddress;
import com.example.discord.identity.RefreshSession;
import com.example.discord.user.UserProfile;
import com.example.discord.user.Username;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
@DependsOn("postgresFlyway")
class JdbcAuthStore implements AuthStore {
    private final DataSource dataSource;

    JdbcAuthStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean saveIfAbsent(AuthAccount account) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertUser(connection, account.profile());
                insertAccount(connection, account);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                if (isUniqueViolation(exception)) {
                    return false;
                }
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save auth account", exception);
        }
    }

    @Override
    public Optional<AuthAccount> findByEmail(EmailAddress email) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT a.email, a.password_hash, u.id, u.username, u.display_name, u.created_at
                 FROM auth_accounts a
                 JOIN users u ON u.id = a.user_id
                 WHERE a.email = ?
                 """)) {
            statement.setString(1, email.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapAccount(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find auth account", exception);
        }
    }

    @Override
    public Optional<UserProfile> findById(UUID id) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, username, display_name, created_at
                 FROM users
                 WHERE id = ?
                 """)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapProfile(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find user profile", exception);
        }
    }

    @Override
    public void revokeAccessToken(String token) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO auth_revoked_access_tokens(token_hash)
                 VALUES (?)
                 ON CONFLICT (token_hash) DO NOTHING
                 """)) {
            statement.setString(1, tokenHash(token));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to revoke access token", exception);
        }
    }

    @Override
    public boolean isAccessTokenRevoked(String token) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT 1
                 FROM auth_revoked_access_tokens
                 WHERE token_hash = ?
                 """)) {
            statement.setString(1, tokenHash(token));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to check revoked access token", exception);
        }
    }

    @Override
    public void saveRefreshSession(RefreshSession session) {
        try (Connection connection = dataSource.getConnection()) {
            upsertRefreshSession(connection, session);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save refresh session", exception);
        }
    }

    @Override
    public Optional<RefreshSession> findRefreshSessionByTokenHash(String tokenHash) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, user_id, token_hash, device_name, created_at, expires_at, revoked_at
                 FROM auth_refresh_sessions
                 WHERE token_hash = ?
                 """)) {
            statement.setString(1, tokenHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRefreshSession(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find refresh session", exception);
        }
    }

    @Override
    public void replaceRefreshSession(RefreshSession revokedPrevious, RefreshSession next) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertRefreshSession(connection, revokedPrevious);
                upsertRefreshSession(connection, next);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to rotate refresh session", exception);
        }
    }

    @Override
    public List<RefreshSession> refreshSessionsForUser(UUID userId) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, user_id, token_hash, device_name, created_at, expires_at, revoked_at
                 FROM auth_refresh_sessions
                 WHERE user_id = ?
                 ORDER BY created_at ASC
                 """)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<RefreshSession> sessions = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    sessions.add(mapRefreshSession(resultSet));
                }
                return List.copyOf(sessions);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list refresh sessions", exception);
        }
    }

    @Override
    public boolean revokeRefreshSession(UUID userId, UUID sessionId, Instant revokedAt) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 UPDATE auth_refresh_sessions
                 SET revoked_at = COALESCE(revoked_at, ?)
                 WHERE id = ?
                   AND user_id = ?
                 """)) {
            statement.setTimestamp(1, Timestamp.from(revokedAt));
            statement.setObject(2, sessionId);
            statement.setObject(3, userId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to revoke refresh session", exception);
        }
    }

    @Override
    public void revokeAllRefreshSessions(UUID userId, Instant revokedAt) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 UPDATE auth_refresh_sessions
                 SET revoked_at = COALESCE(revoked_at, ?)
                 WHERE user_id = ?
                 """)) {
            statement.setTimestamp(1, Timestamp.from(revokedAt));
            statement.setObject(2, userId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to revoke refresh sessions", exception);
        }
    }

    @Override
    public boolean grantGlobalRole(UUID userId, String role) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO user_global_roles(user_id, role)
                 VALUES (?, ?)
                 ON CONFLICT (user_id, role) DO NOTHING
                 """)) {
            statement.setObject(1, userId);
            statement.setString(2, GlobalRole.canonical(role));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to grant global role", exception);
        }
    }

    @Override
    public boolean revokeGlobalRole(UUID userId, String role) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 DELETE FROM user_global_roles
                 WHERE user_id = ?
                   AND role = ?
                 """)) {
            statement.setObject(1, userId);
            statement.setString(2, GlobalRole.canonical(role));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to revoke global role", exception);
        }
    }

    @Override
    public List<String> globalRolesForUser(UUID userId) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT role
                 FROM user_global_roles
                 WHERE user_id = ?
                 ORDER BY role ASC
                 """)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> roles = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    roles.add(resultSet.getString("role"));
                }
                return List.copyOf(roles);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list global roles", exception);
        }
    }

    @Override
    public void recordGlobalRoleAudit(GlobalRoleAuditEntry entry) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO user_global_role_audit_log(target_user_id, role, action, actor, result, occurred_at)
                 VALUES (?, ?, ?, ?, ?, ?)
                 """)) {
            statement.setObject(1, entry.targetUserId());
            statement.setString(2, GlobalRole.canonical(entry.role()));
            statement.setString(3, entry.action().name());
            statement.setString(4, entry.actor());
            statement.setString(5, entry.result().name());
            statement.setTimestamp(6, Timestamp.from(entry.occurredAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to record global role audit log", exception);
        }
    }

    @Override
    public List<GlobalRoleAuditEntry> globalRoleAuditLog(UUID userId) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT target_user_id, role, action, actor, result, occurred_at
                 FROM user_global_role_audit_log
                 WHERE target_user_id = ?
                 ORDER BY occurred_at ASC
                 """)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<GlobalRoleAuditEntry> entries = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapGlobalRoleAuditEntry(resultSet));
                }
                return List.copyOf(entries);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list global role audit log", exception);
        }
    }

    @Override
    public List<GlobalRoleAuditEntry> globalRoleAuditLog(UUID userId, int limit) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT target_user_id, role, action, actor, result, occurred_at
                 FROM user_global_role_audit_log
                 WHERE target_user_id = ?
                 ORDER BY occurred_at DESC
                 LIMIT ?
                 """)) {
            statement.setObject(1, userId);
            statement.setInt(2, Math.max(0, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<GlobalRoleAuditEntry> entries = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapGlobalRoleAuditEntry(resultSet));
                }
                return List.copyOf(entries);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list global role audit log", exception);
        }
    }

    @Override
    public List<GlobalRoleAuditEntry> globalRoleAuditLog(int limit) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT target_user_id, role, action, actor, result, occurred_at
                 FROM user_global_role_audit_log
                 ORDER BY occurred_at DESC
                 LIMIT ?
                 """)) {
            statement.setInt(1, Math.max(0, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<GlobalRoleAuditEntry> entries = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapGlobalRoleAuditEntry(resultSet));
                }
                return List.copyOf(entries);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list global role audit log", exception);
        }
    }

    private static void insertUser(Connection connection, UserProfile profile) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO users(id, username, display_name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """)) {
            Timestamp createdAt = Timestamp.from(profile.createdAt());
            statement.setObject(1, profile.id());
            statement.setString(2, profile.username().value());
            statement.setString(3, profile.displayName());
            statement.setTimestamp(4, createdAt);
            statement.setTimestamp(5, createdAt);
            statement.executeUpdate();
        }
    }

    private static void insertAccount(Connection connection, AuthAccount account) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO auth_accounts(email, user_id, password_hash)
            VALUES (?, ?, ?)
            """)) {
            statement.setString(1, account.email().value());
            statement.setObject(2, account.profile().id());
            statement.setString(3, account.passwordHash());
            statement.executeUpdate();
        }
    }

    private static AuthAccount mapAccount(ResultSet resultSet) throws SQLException {
        return new AuthAccount(
            EmailAddress.from(resultSet.getString("email")),
            resultSet.getString("password_hash"),
            mapProfile(resultSet)
        );
    }

    private static void upsertRefreshSession(Connection connection, RefreshSession session) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO auth_refresh_sessions(id, user_id, token_hash, device_name, created_at, expires_at, revoked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET token_hash = EXCLUDED.token_hash,
                device_name = EXCLUDED.device_name,
                expires_at = EXCLUDED.expires_at,
                revoked_at = EXCLUDED.revoked_at
            """)) {
            statement.setObject(1, session.id());
            statement.setObject(2, session.userId());
            statement.setString(3, session.tokenHash());
            statement.setString(4, session.deviceName());
            statement.setTimestamp(5, Timestamp.from(session.createdAt()));
            statement.setTimestamp(6, Timestamp.from(session.expiresAt()));
            if (session.revokedAt().isPresent()) {
                statement.setTimestamp(7, Timestamp.from(session.revokedAt().get()));
            } else {
                statement.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            statement.executeUpdate();
        }
    }

    private static RefreshSession mapRefreshSession(ResultSet resultSet) throws SQLException {
        Timestamp revokedAt = resultSet.getTimestamp("revoked_at");
        return new RefreshSession(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("user_id", UUID.class),
            resultSet.getString("token_hash").trim(),
            resultSet.getString("device_name"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("expires_at").toInstant(),
            Optional.ofNullable(revokedAt).map(Timestamp::toInstant)
        );
    }

    private static UserProfile mapProfile(ResultSet resultSet) throws SQLException {
        Instant createdAt = resultSet.getTimestamp("created_at").toInstant();
        return UserProfile.create(
            resultSet.getObject("id", UUID.class),
            Username.from(resultSet.getString("username")),
            resultSet.getString("display_name"),
            createdAt
        );
    }

    private static GlobalRoleAuditEntry mapGlobalRoleAuditEntry(ResultSet resultSet) throws SQLException {
        return new GlobalRoleAuditEntry(
            resultSet.getObject("target_user_id", UUID.class),
            resultSet.getString("role"),
            GlobalRoleAuditAction.valueOf(resultSet.getString("action")),
            resultSet.getString("actor"),
            GlobalRoleAuditResult.valueOf(resultSet.getString("result")),
            resultSet.getTimestamp("occurred_at").toInstant()
        );
    }

    private static boolean isUniqueViolation(SQLException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}
