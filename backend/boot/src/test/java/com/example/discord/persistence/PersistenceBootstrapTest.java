package com.example.discord.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class PersistenceBootstrapTest {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void postgresProfileRunsFlywayAndCreatesCoreTables() throws Exception {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.getConfiguration().getLocations())
            .extracting(location -> location.getDescriptor())
            .contains("classpath:db/migration");
        assertThat(flyway.getConfiguration().isBaselineOnMigrate()).isTrue();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            assertThat(tableExists(statement, "users")).isTrue();
            assertThat(tableExists(statement, "guilds")).isTrue();
            assertThat(tableExists(statement, "channels")).isTrue();
            assertThat(tableExists(statement, "messages")).isTrue();
            assertThat(tableExists(statement, "message_idempotency_keys")).isTrue();
            assertThat(tableExists(statement, "message_read_projection")).isTrue();
            assertThat(tableExists(statement, "message_publication_outbox")).isTrue();
            assertThat(columnExists(statement, "message_publication_outbox", "claim_token")).isTrue();
            assertThat(columnExists(statement, "message_publication_outbox", "dead_lettered_at")).isTrue();
            assertThat(tableExists(statement, "invites")).isTrue();
        }
    }

    private static boolean tableExists(Statement statement, String tableName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = '%s'
            )
            """.formatted(tableName))) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private static boolean columnExists(Statement statement, String tableName, String columnName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = '%s'
                  AND column_name = '%s'
            )
            """.formatted(tableName, columnName))) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }
}
