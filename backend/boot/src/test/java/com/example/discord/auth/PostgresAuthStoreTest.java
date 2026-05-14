package com.example.discord.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.identity.EmailAddress;
import com.example.discord.user.UserProfile;
import com.example.discord.user.Username;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("postgres")
class PostgresAuthStoreTest {
    @Autowired
    private AuthStore store;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanAuthTables() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM channel_role_overwrites");
            statement.executeUpdate("DELETE FROM guild_member_roles");
            statement.executeUpdate("DELETE FROM channels");
            statement.executeUpdate("DELETE FROM guild_roles");
            statement.executeUpdate("DELETE FROM guild_members");
            statement.executeUpdate("DELETE FROM guilds");
            statement.executeUpdate("DELETE FROM auth_revoked_access_tokens");
            statement.executeUpdate("DELETE FROM auth_accounts");
            statement.executeUpdate("DELETE FROM users");
        }
    }

    @Test
    void storesAccountsAndRevokedTokensInPostgres() {
        UUID userId = UUID.randomUUID();
        EmailAddress email = EmailAddress.from("persist-" + userId + "@example.com");
        UserProfile profile = UserProfile.create(
            userId,
            Username.from("persist" + userId.toString().substring(0, 8)),
            "Persisted User",
            Instant.parse("2026-01-01T00:00:00Z")
        );
        AuthAccount account = new AuthAccount(email, "hashed-password", profile);

        assertThat(store.saveIfAbsent(account)).isTrue();
        assertThat(store.saveIfAbsent(account)).isFalse();
        assertThat(store.findByEmail(email)).contains(account);
        assertThat(store.findById(userId)).contains(profile);

        String token = "token-" + UUID.randomUUID();
        store.revokeAccessToken(token);

        assertThat(store.isAccessTokenRevoked(token)).isTrue();
        assertThat(store.isAccessTokenRevoked("other-" + token)).isFalse();
    }
}
