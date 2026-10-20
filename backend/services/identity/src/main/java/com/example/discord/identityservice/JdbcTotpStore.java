package com.example.discord.identityservice;

import com.example.discord.identity.TotpVerifier;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JdbcTotpStore {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcTotpStore.class);
    private final DataSource dataSource;
    private final SecretCipher cipher;

    JdbcTotpStore(DataSource dataSource, SecretCipher cipher) {
        this.dataSource = dataSource;
        this.cipher = cipher;
    }

    boolean enroll(UUID owner, byte[] secret, Instant expiresAt) {
        if (secret == null || secret.length < 16 || secret.length > 64) {
            throw new IllegalArgumentException("invalid TOTP secret length");
        }
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO mfa_totp (user_id, secret_ciphertext, confirmation_expires_at, active, consumed_step, failures) VALUES (?, ?, ?, FALSE, -1, 0)")) {
            statement.setString(1, owner.toString());
            statement.setString(2, cipher.encrypt(owner, secret));
            statement.setLong(3, expiresAt.toEpochMilli());
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                return false;
            }
            throw new IllegalStateException("MFA enrollment could not be stored");
        }
    }

    boolean verify(UUID owner, String code, Instant now, boolean confirmation) {
        return verify(owner, code, now, confirmation, null);
    }

    UUID challenge(UUID owner, Instant now) {
        UUID id = UUID.randomUUID();
        try (var connection = dataSource.getConnection(); var update = connection.prepareStatement(
                "UPDATE mfa_totp SET challenge_id = ?, challenge_created_at = ?, challenge_expires_at = ? WHERE user_id = ? AND active = TRUE AND failures < 5 AND (challenge_created_at IS NULL OR challenge_created_at <= ?)")) {
            update.setString(1, id.toString());
            update.setLong(2, now.toEpochMilli());
            update.setLong(3, now.plusSeconds(180).toEpochMilli());
            update.setString(4, owner.toString());
            update.setLong(5, now.minusSeconds(30).toEpochMilli());
            if (update.executeUpdate() != 1) {
                throw new IllegalArgumentException("MFA challenge unavailable");
            }
            return id;
        } catch (SQLException exception) {
            throw new IllegalStateException("MFA challenge storage unavailable");
        }
    }

    void unlock(UUID owner) {
        try (var connection = dataSource.getConnection(); var update = connection.prepareStatement(
                "UPDATE mfa_totp SET failures = 0, challenge_id = NULL, challenge_created_at = NULL, challenge_expires_at = NULL WHERE user_id = ?")) {
            update.setString(1, owner.toString());
            if (update.executeUpdate() != 1) throw new IllegalArgumentException("MFA account unavailable");
        } catch (SQLException exception) {
            throw new IllegalStateException("MFA unlock unavailable");
        }
    }

    void reset(UUID owner, byte[] secret, Instant expiresAt) {
        if (secret == null || secret.length < 16 || secret.length > 64) throw new IllegalArgumentException("invalid TOTP secret length");
        String encrypted = cipher.encrypt(owner, secret);
        try (var connection = dataSource.getConnection(); var update = connection.prepareStatement(
                "UPDATE mfa_totp SET pending_secret_ciphertext = ?, pending_expires_at = ?, challenge_id = NULL, challenge_created_at = NULL, challenge_expires_at = NULL WHERE user_id = ? AND active = TRUE AND failures < 5")) {
            update.setString(1, encrypted);
            update.setLong(2, expiresAt.toEpochMilli());
            update.setString(3, owner.toString());
            if (update.executeUpdate() != 1) throw new IllegalArgumentException("MFA account unavailable");
        } catch (SQLException exception) {
            throw new IllegalStateException("MFA reset unavailable");
        }
    }

    boolean complete(UUID owner, UUID challengeId, String code, Instant now) {
        java.util.Objects.requireNonNull(challengeId, "challengeId");
        return verify(owner, code, now, false, challengeId);
    }

    private boolean verify(UUID owner, String code, Instant now, boolean confirmation, UUID challengeId) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean accepted = false;
                boolean rotated = false;
                try (var query = connection.prepareStatement("SELECT * FROM mfa_totp WHERE user_id = ? FOR UPDATE")) {
                    query.setString(1, owner.toString());
                    try (var row = query.executeQuery()) {
                        if (row.next() && (confirmation ? (!row.getBoolean("active") || pendingValid(row, now)) : row.getBoolean("active"))
                                && row.getInt("failures") < 5
                                && (!confirmation || (pendingValid(row, now) || now.toEpochMilli() < row.getLong("confirmation_expires_at")))
                                && (challengeId == null || (challengeId.toString().equals(row.getString("challenge_id"))
                                    && now.toEpochMilli() < row.getLong("challenge_expires_at")))) {
                            boolean pending = confirmation && pendingValid(row, now);
                            byte[] secret = cipher.decrypt(owner, pending ? row.getString("pending_secret_ciphertext") : row.getString("secret_ciphertext"));
                            java.util.OptionalLong matched;
                            try {
                                matched = TotpVerifier.match(secret, code, now, pending ? -1 : row.getLong("consumed_step"));
                            } finally {
                                Arrays.fill(secret, (byte) 0);
                            }
                            accepted = matched.isPresent();
                            rotated = pending && accepted;
                            try (var update = connection.prepareStatement(pending && accepted
                                    ? "UPDATE mfa_totp SET secret_ciphertext = pending_secret_ciphertext, pending_secret_ciphertext = NULL, pending_expires_at = NULL, active = TRUE, consumed_step = ?, failures = 0, challenge_id = NULL, challenge_created_at = NULL, challenge_expires_at = NULL WHERE user_id = ?"
                                    : "UPDATE mfa_totp SET active = ?, consumed_step = ?, failures = ?, challenge_id = ?, challenge_created_at = challenge_created_at, challenge_expires_at = CASE WHEN ? THEN NULL ELSE challenge_expires_at END, pending_secret_ciphertext = CASE WHEN ? THEN NULL ELSE pending_secret_ciphertext END, pending_expires_at = CASE WHEN ? THEN NULL ELSE pending_expires_at END WHERE user_id = ?")) {
                                if (pending && accepted) {
                                    update.setLong(1, matched.orElse(row.getLong("consumed_step")));
                                    update.setString(2, owner.toString());
                                } else {
                                update.setBoolean(1, row.getBoolean("active") || accepted);
                                update.setLong(2, matched.orElse(row.getLong("consumed_step")));
                                update.setInt(3, accepted ? 0 : row.getInt("failures") + 1);
                                boolean consumeChallenge = accepted || challengeId != null;
                                update.setString(4, consumeChallenge ? null : row.getString("challenge_id"));
                                update.setBoolean(5, consumeChallenge);
                                update.setBoolean(6, pending && !accepted && now.toEpochMilli() >= row.getLong("pending_expires_at"));
                                update.setBoolean(7, pending && !accepted && now.toEpochMilli() >= row.getLong("pending_expires_at"));
                                update.setString(8, owner.toString());
                                }
                                update.executeUpdate();
                            }
                        }
                    }
                }
                connection.commit();
                if (rotated) LOG.info("totp secret rotated user={} result=204", owner);
                return accepted;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("MFA verification unavailable");
        }
    }

    private static boolean pendingValid(java.sql.ResultSet row, Instant now) throws SQLException {
        return row.getString("pending_secret_ciphertext") != null && now.toEpochMilli() < row.getLong("pending_expires_at");
    }
}
