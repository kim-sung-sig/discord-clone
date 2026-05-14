package com.example.discord.auth;

import com.example.discord.identity.EmailAddress;
import com.example.discord.user.UserProfile;
import com.example.discord.user.Username;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
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

    private static UserProfile mapProfile(ResultSet resultSet) throws SQLException {
        Instant createdAt = resultSet.getTimestamp("created_at").toInstant();
        return UserProfile.create(
            resultSet.getObject("id", UUID.class),
            Username.from(resultSet.getString("username")),
            resultSet.getString("display_name"),
            createdAt
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
