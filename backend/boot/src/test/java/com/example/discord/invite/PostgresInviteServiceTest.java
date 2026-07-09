package com.example.discord.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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
class PostgresInviteServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-13T00:00:00Z");

    @Autowired
    private InviteSnapshotStore snapshots;

    @Autowired
    private DataSource dataSource;

    private UUID ownerId;
    private UUID memberId;
    private UUID guildId;
    private UUID channelId;

    @BeforeEach
    void cleanAndSeed() throws Exception {
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        guildId = UUID.randomUUID();
        channelId = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM invite_acceptances");
            statement.executeUpdate("DELETE FROM invite_role_grants");
            statement.executeUpdate("DELETE FROM invites");
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
        insertUser(memberId, "member" + memberId.toString().substring(0, 8));
        insertGuildAndChannel();
    }

    @Test
    void persistsInvitesAndReloadsAcceptedMembersAndDeletion() {
        PersistentInviteService service = new PersistentInviteService(snapshots, java.time.Clock.systemUTC());
        Invite created = service.create(new CreateInviteCommand(guildId, channelId, ownerId, 0, 5, false, java.util.List.of()));
        service.accept(created.code(), memberId);
        service.delete(created.code());

        PersistentInviteService reloaded = new PersistentInviteService(snapshots, java.time.Clock.systemUTC());
        Invite invite = reloaded.get(created.code());

        assertThat(invite.uses()).isEqualTo(1);
        assertThat(invite.acceptedMemberIds()).containsExactly(memberId);
        assertThat(invite.deletedAt()).isNotNull();
    }

    @Test
    void rejectsExpiredPersistedInviteAccept() {
        MutableClock clock = new MutableClock(NOW);
        PersistentInviteService service = new PersistentInviteService(snapshots, clock);
        Invite invite = service.create(new CreateInviteCommand(guildId, channelId, ownerId, 60, 0, false, List.of()));

        clock.advanceSeconds(61);
        PersistentInviteService reloaded = new PersistentInviteService(snapshots, clock);

        assertThatThrownBy(() -> reloaded.accept(invite.code(), UUID.randomUUID()))
            .isInstanceOf(InviteExpiredException.class);
    }

    @Test
    void enforcesPersistedMaxUsesUnderConcurrentAccepts() throws Exception {
        PersistentInviteService service = new PersistentInviteService(snapshots, Clock.fixed(NOW, ZoneOffset.UTC));
        Invite invite = service.create(new CreateInviteCommand(guildId, channelId, ownerId, 0, 1, false, List.of()));
        List<UUID> acceptorIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        insertUser(acceptorIds.get(0), "acceptor" + acceptorIds.get(0).toString().substring(0, 8));
        insertUser(acceptorIds.get(1), "acceptor" + acceptorIds.get(1).toString().substring(0, 8));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                UUID acceptorId = acceptorIds.get(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        service.accept(invite.code(), acceptorId);
                        accepted.incrementAndGet();
                    } catch (InviteMaxUsesExceededException ignored) {
                        // Expected for exactly one concurrent distinct accept.
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        PersistentInviteService reloaded = new PersistentInviteService(snapshots, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(accepted).hasValue(1);
        assertThat(reloaded.preview(invite.code()).uses()).isEqualTo(1);
    }

    @Test
    void sameMemberPersistedAcceptIsIdempotentAndDoesNotConsumeAdditionalUse() {
        PersistentInviteService service = new PersistentInviteService(snapshots, Clock.fixed(NOW, ZoneOffset.UTC));
        Invite invite = service.create(new CreateInviteCommand(guildId, channelId, ownerId, 0, 1, false, List.of()));

        InviteAcceptResult first = service.accept(invite.code(), memberId);
        InviteAcceptResult second = service.accept(invite.code(), memberId);
        PersistentInviteService reloaded = new PersistentInviteService(snapshots, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(first.alreadyAccepted()).isFalse();
        assertThat(second.alreadyAccepted()).isTrue();
        assertThat(reloaded.preview(invite.code()).uses()).isEqualTo(1);
    }

    @Test
    void rejectsDeletedPersistedInviteReuse() {
        PersistentInviteService service = new PersistentInviteService(snapshots, Clock.fixed(NOW, ZoneOffset.UTC));
        Invite invite = service.create(new CreateInviteCommand(guildId, channelId, ownerId, 0, 0, false, List.of()));

        service.delete(invite.code());
        PersistentInviteService reloaded = new PersistentInviteService(snapshots, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> reloaded.accept(invite.code(), UUID.randomUUID()))
            .isInstanceOf(InviteDeletedException.class);
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

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
