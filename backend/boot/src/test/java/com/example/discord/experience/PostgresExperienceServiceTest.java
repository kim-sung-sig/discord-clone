package com.example.discord.experience;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class PostgresExperienceServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-15T00:00:00Z");

    @Autowired
    private EntitlementStore entitlements;

    @Autowired
    private DataSource dataSource;

    private UUID userId;
    private UUID guildId;

    @BeforeEach
    void cleanAndSeed() throws Exception {
        userId = UUID.randomUUID();
        guildId = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
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
        insertUser(userId, "premium" + userId.toString().substring(0, 8));
        insertGuild(guildId, userId);
    }

    @Test
    void duplicateProviderSubscriptionGrantIsIdempotentInPostgres() {
        InMemoryExperienceService service = new InMemoryExperienceService(
            Clock.fixed(NOW, ZoneOffset.UTC),
            entitlements
        );
        String featureKey = PremiumFeature.HD_STREAMING.key();

        Entitlement first = service.grantEntitlement(userId, guildId, featureKey, "local_test", "sub-dup", NOW.plusSeconds(3600));
        Entitlement second = service.grantEntitlement(userId, guildId, featureKey, "local_test", "sub-dup", NOW.plusSeconds(3600));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(service.entitlementsForUserFeature(userId, featureKey)).hasSize(1);
    }

    @Test
    void activeCanceledAndExpiredEntitlementsUsePostgresState() {
        InMemoryExperienceService service = new InMemoryExperienceService(
            Clock.fixed(NOW, ZoneOffset.UTC),
            entitlements
        );
        String featureKey = PremiumFeature.HD_STREAMING.key();

        assertThat(service.hasEntitlement(userId, featureKey)).isFalse();

        Entitlement active = service.grantEntitlement(userId, guildId, featureKey, "local_test", "sub-active", NOW.plusSeconds(3600));
        assertThat(active.status()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(service.hasEntitlement(userId, featureKey)).isTrue();

        service.cancelEntitlement(active.id());
        assertThat(service.hasEntitlement(userId, featureKey)).isFalse();

        Entitlement expired = service.grantEntitlement(userId, guildId, featureKey, "local_test", "sub-expired", NOW.minusSeconds(1));
        assertThat(expired.status()).isEqualTo(EntitlementStatus.EXPIRED);
        assertThat(service.hasEntitlement(userId, featureKey)).isFalse();
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

    private void insertGuild(UUID id, UUID ownerId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("INSERT INTO guilds(id, name, owner_id) VALUES (?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, "premium guild");
            statement.setObject(3, ownerId);
            statement.executeUpdate();
        }
    }
}
