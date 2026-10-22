package com.example.discord.guild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class PostgresGuildStoreContractTest {
    private DataSource dataSource;

    @BeforeAll
    void migrateOnlyTaskDatabase() throws Exception {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setUrl(System.getenv("POSTGRES_JDBC_URL"));
        postgres.setUser(System.getenv().getOrDefault("POSTGRES_USER", "dev_user"));
        postgres.setPassword(System.getenv().getOrDefault("POSTGRES_PASSWORD", "dev_password"));
        try (var connection = postgres.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT current_database()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).startsWith("discord_runtime_guild_");
        }
        dataSource = postgres;
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @Test
    void alreadyRunningServicesReadOtherInstancesCommittedChanges() throws Exception {
        UUID owner = insertUser();
        UUID member = insertUser();
        var writer = service();
        Guild guild = writer.createGuild("cross-instance", owner);
        var reader = service();

        writer.addMember(guild.id(), member);

        assertThat(reader.guild(guild.id()).members()).extracting(GuildMember::userId).contains(member);
        Role manager = writer.createRole(guild.id(), "manager", PermissionSet.empty().grant(Permission.MANAGE_ROLES));
        writer.assignRoleToMember(guild.id(), member, manager.id());
        Channel channel = writer.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        assertThat(reader.canManageRoles(guild.id(), member)).isTrue();
        assertThat(reader.channel(guild.id(), channel.id())).isEqualTo(channel);
        assertThat(reader.guildIdsForMember(member)).contains(guild.id());
        assertThat(reader.guildIdForChannel(channel.id())).isEqualTo(guild.id());
    }

    @Test
    void membershipChangesPreserveMessagesAndEvents() throws Exception {
        UUID owner = insertUser();
        UUID member = insertUser();
        var service = service();
        Guild guild = service.createGuild("preserve-channel-data", owner);
        Channel parent = service.createChannel(guild.id(), "category", ChannelType.GUILD_CATEGORY, null);
        Channel channel = service.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, parent.id());
        Role role = service.createRole(guild.id(), "invited-role");
        UUID messageId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var message = connection.prepareStatement(
                 "INSERT INTO messages(id,guild_id,channel_id,author_id,sequence,content) VALUES (?,?,?,?,1,'data')");
             var event = connection.prepareStatement(
                 "INSERT INTO server_events(id,guild_id,channel_id,creator_id,title,starts_at,ends_at,status,updated_at) "
                     + "VALUES (?,?,?,?,'event',now(),now()+interval '1 hour','SCHEDULED',now())")) {
            bindDependent(message, messageId, guild.id(), channel.id(), owner);
            message.executeUpdate();
            bindDependent(event, eventId, guild.id(), channel.id(), owner);
            event.executeUpdate();
        }

        String inviteCode = UUID.randomUUID().toString();
        try (var connection = dataSource.getConnection();
             var invite = connection.prepareStatement(
                 "INSERT INTO invites(code,guild_id,channel_id,creator_id,max_age_seconds,max_uses) VALUES (?,?,?,?,0,0)");
             var grant = connection.prepareStatement("INSERT INTO invite_role_grants(code,role_id) VALUES (?,?)")) {
            invite.setString(1, inviteCode);
            invite.setObject(2, guild.id());
            invite.setObject(3, channel.id());
            invite.setObject(4, owner);
            invite.executeUpdate();
            grant.setString(1, inviteCode);
            grant.setObject(2, role.id());
            grant.executeUpdate();
        }
        service.addMember(guild.id(), member);
        service.assignRolePermissions(guild.id(), role.id(), PermissionSet.empty().grant(Permission.VIEW_CHANNEL));
        service.addChannelRoleOverwrite(guild.id(), channel.id(), role.id(), PermissionSet.empty(),
            PermissionSet.empty().grant(Permission.SEND_MESSAGES));

        assertThat(service().channel(guild.id(), channel.id()).parentId()).isEqualTo(parent.id());
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT 1 FROM invite_role_grants WHERE code=? AND role_id=?")) {
            statement.setString(1, inviteCode);
            statement.setObject(2, role.id());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
            }
        }
        assertThat(exists("messages", messageId)).isTrue();
        assertThat(exists("server_events", eventId)).isTrue();
    }

    @Test
    void failedWriteDoesNotPoisonTheLiveServiceOrAdvanceAuthorization() throws Exception {
        UUID owner = insertUser();
        var service = service();
        Guild guild = service.createGuild("rollback", owner);
        long version = permissionVersion(guild.id());
        long outbox = outboxCount(guild.id());
        PermissionSet beforePermissions = service.roles(guild.id()).getFirst().permissions();

        assertThatThrownBy(() -> service.createChannel(
            guild.id(), "invalid", ChannelType.GUILD_TEXT, UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class);

        assertThat(service.guild(guild.id()).channels()).isEmpty();
        assertThat(permissionVersion(guild.id())).isEqualTo(version);
        assertThat(service().guild(guild.id()).channels()).isEmpty();
        assertThat(service.roles(guild.id()).getFirst().permissions()).isEqualTo(beforePermissions);
        assertThat(outboxCount(guild.id())).isEqualTo(outbox);
        service.createChannel(guild.id(), "recovered", ChannelType.GUILD_TEXT, null);
        assertThat(service().guild(guild.id()).channels()).extracting(Channel::name).containsExactly("recovered");
        assertThat(permissionVersion(guild.id())).isEqualTo(version + 1);
    }

    @Test
    void concurrentServicesKeepBothCommittedChannelChanges() throws Exception {
        UUID owner = insertUser();
        var first = service();
        Guild guild = first.createGuild("concurrent", owner);
        var second = service();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return first.createChannel(guild.id(), "one", ChannelType.GUILD_TEXT, null);
            });
            var two = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return second.createChannel(guild.id(), "two", ChannelType.GUILD_TEXT, null);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
        }

        assertThat(service().guild(guild.id()).channels()).extracting(Channel::name)
            .containsExactlyInAnyOrder("one", "two");
    }

    @Test
    void readDoesNotWaitForAConcurrentGuildWriterAndSeesOnlyCommittedState() throws Exception {
        UUID owner = insertUser();
        Guild guild = service().createGuild("committed-name", owner);
        try (Connection held = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            held.setAutoCommit(false);
            try (var statement = held.prepareStatement("UPDATE guilds SET name='uncommitted-name' WHERE id=?")) {
                statement.setObject(1, guild.id());
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            try {
                Guild read = executor.submit(() -> service().guild(guild.id())).get(2, TimeUnit.SECONDS);
                assertThat(read.name()).isEqualTo("committed-name");
            } finally {
                held.rollback();
            }
        }
    }

    @Test
    void readLoadsEveryTableFromTheSnapshotTakenByItsFirstQuery() throws Exception {
        UUID owner = insertUser();
        Guild guild = service().createGuild("snapshot", owner);
        UUID lateChannel = UUID.randomUUID();
        AtomicBoolean committed = new AtomicBoolean();
        DataSource commitsChannelAfterGuildRow = interceptingDataSource(sql -> {
            // Runs after the guild row query took the snapshot and before the channel table is read.
            if (sql.contains("FROM guild_roles") && committed.compareAndSet(false, true)) {
                insertChannel(lateChannel, guild.id());
            }
        });

        Guild read = new GuildService(new JdbcGuildStore(commitsChannelAfterGuildRow)).guild(guild.id());

        assertThat(committed).isTrue();
        assertThat(read.channels()).extracting(Channel::id).doesNotContain(lateChannel);
        assertThat(service().guild(guild.id()).channels()).extracting(Channel::id).contains(lateChannel);
    }

    private DataSource interceptingDataSource(SqlHook beforePrepare) {
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class},
            (proxy, method, args) -> {
                Object result = invoke(method, dataSource, args);
                if (!"getConnection".equals(method.getName())) {
                    return result;
                }
                Connection connection = (Connection) result;
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (connectionProxy, call, callArgs) -> {
                        if ("prepareStatement".equals(call.getName())) {
                            beforePrepare.accept((String) callArgs[0]);
                        }
                        return invoke(call, connection, callArgs);
                    });
            });
    }

    private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private void insertChannel(UUID channelId, UUID guildId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "INSERT INTO channels(id, guild_id, parent_id, name, type, position) VALUES (?, ?, NULL, 'late', ?, 99)")) {
            statement.setObject(1, channelId);
            statement.setObject(2, guildId);
            statement.setString(3, ChannelType.GUILD_TEXT.name());
            statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface SqlHook {
        void accept(String sql) throws Exception;
    }

    @Test
    void freshAdapterReadsPersistedGuildWithoutStartupLoadingAllGuilds() throws Exception {
        UUID owner = insertUser();
        var writer = service();
        Guild guild = writer.createGuild("fresh-read", owner);
        Channel channel = writer.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);

        assertThat(service().channel(guild.id(), channel.id())).isEqualTo(channel);
    }

    private GuildService service() {
        return new GuildService(new JdbcGuildStore(dataSource));
    }

    private UUID insertUser() throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "INSERT INTO users(id,username,display_name) VALUES (?,?,'Guild Test')")) {
            statement.setObject(1, id);
            statement.setString(2, id.toString().replace("-", ""));
            statement.executeUpdate();
        }
        return id;
    }

    private boolean exists(String table, UUID id) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE id=?")) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private long outboxCount(UUID guildId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "SELECT count(*) FROM authorization_projection_outbox WHERE guild_id=?")) {
            statement.setObject(1, guildId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }

    private long permissionVersion(UUID guildId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "SELECT permission_version FROM guild_authorization_versions WHERE guild_id=?")) {
            statement.setObject(1, guildId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }

    private static void bindDependent(
        java.sql.PreparedStatement statement, UUID id, UUID guildId, UUID channelId, UUID owner
    ) throws Exception {
        statement.setObject(1, id);
        statement.setObject(2, guildId);
        statement.setObject(3, channelId);
        statement.setObject(4, owner);
    }
}
