package com.example.discord.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
@Testcontainers
class PostgresPoolIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void postgresProfileWiresAndClosesActivePool() throws Exception {
        try (var disabled = new AnnotationConfigApplicationContext()) {
            disabled.register(PostgresPersistenceConfiguration.class);
            disabled.refresh();
            assertThat(disabled.getBeansOfType(HikariDataSource.class)).isEmpty();
        }
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("postgres");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
            "spring.datasource.url", POSTGRES.getJdbcUrl(),
            "spring.datasource.username", POSTGRES.getUsername(),
            "spring.datasource.password", POSTGRES.getPassword(),
            "spring.datasource.hikari.maximum-pool-size", "1")));
        context.register(PostgresPersistenceConfiguration.class);
        context.refresh();
        var pool = context.getBean(HikariDataSource.class);
        try {
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.getDataSource()).isSameAs(pool);
            assertThat(context.getBean(DataSourceTransactionManager.class).getDataSource()).isSameAs(pool);
            assertThat(context.getBean(Flyway.class).getConfiguration().getDataSource()).isSameAs(pool);
            assertThat(context.getBean(Flyway.class).info().current()).isNotNull();
            int pid = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            assertThat(jdbc.queryForObject("select pg_backend_pid()", Integer.class)).isEqualTo(pid);
            pool.setConnectionTimeout(250);
            try (var held = pool.getConnection()) {
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
                assertThatThrownBy(pool::getConnection).isInstanceOf(SQLException.class);
            }
            assertThat(jdbc.queryForObject("select pg_backend_pid()", Integer.class)).isEqualTo(pid);
        } finally {
            context.close();
        }
        assertThat(pool.isClosed()).isTrue();
    }
}
