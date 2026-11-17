package com.example.discord.identityservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class MfaDataSourceTest {
    @Test
    void actualMfaConfigurationWiresAndClosesActivePool() throws Exception {
        try (var disabled = new AnnotationConfigApplicationContext()) {
            disabled.register(MfaConfiguration.class);
            disabled.refresh();
            assertThat(disabled.getBeansOfType(HikariDataSource.class)).isEmpty();
        }
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
            "discord.auth.mfa.enabled", "true",
            "discord.auth.mfa.jdbc-url", "jdbc:h2:mem:mfa_pool_wiring;DB_CLOSE_DELAY=-1",
            "discord.auth.mfa.database-user", "sa",
            "discord.auth.mfa.database-password", "",
            "discord.auth.mfa.maximum-pool-size", "1",
            "discord.auth.mfa.encryption-key", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")));
        context.register(MfaConfiguration.class);
        context.refresh();
        var pool = context.getBean(HikariDataSource.class);
        try {
            assertThat(ReflectionTestUtils.getField(context.getBean(JdbcTotpStore.class), "dataSource")).isSameAs(pool);
            pool.setConnectionTimeout(250);
            org.h2.jdbc.JdbcConnection physical;
            try (var held = pool.getConnection()) {
                physical = held.unwrap(org.h2.jdbc.JdbcConnection.class);
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
                assertThatThrownBy(pool::getConnection).isInstanceOf(SQLException.class);
            }
            try (var recovered = pool.getConnection()) {
                assertThat(recovered.isValid(1)).isTrue();
                assertThat(recovered.unwrap(org.h2.jdbc.JdbcConnection.class)).isSameAs(physical);
                assertThat(pool.getHikariPoolMXBean().getTotalConnections()).isEqualTo(1);
            }
        } finally {
            context.close();
        }
        assertThat(pool.isClosed()).isTrue();
    }

    @Test
    void mfaDataSourceUsesConnectionPool() {
        var env = new MockEnvironment()
            .withProperty("discord.auth.mfa.jdbc-url", "jdbc:postgresql://localhost/example")
            .withProperty("discord.auth.mfa.database-user", "test-user")
            .withProperty("discord.auth.mfa.database-password", "test-password");
        var dataSource = new MfaConfiguration().mfaDataSource(env);
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
        assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost/example");
        assertThat(dataSource.getUsername()).isEqualTo("test-user");
        assertThat(dataSource.getPassword()).isEqualTo("test-password");
        assertThat(dataSource.getPoolName()).isEqualTo("discord-identity-mfa");
        dataSource.close();
        env.setProperty("DISCORD_AUTH_MFA_MAXIMUM_POOL_SIZE", "3");
        try (var overridden = new MfaConfiguration().mfaDataSource(env)) {
            assertThat(overridden.getMaximumPoolSize()).isEqualTo(3);
        }
        env.setProperty("discord.auth.mfa.maximum-pool-size", "4");
        var context = new AnnotationConfigApplicationContext();
        context.registerBean("pool", com.zaxxer.hikari.HikariDataSource.class,
            () -> new MfaConfiguration().mfaDataSource(env));
        context.refresh();
        var pool = context.getBean(com.zaxxer.hikari.HikariDataSource.class);
        assertThat(pool.getMaximumPoolSize()).isEqualTo(4);
        context.close();
        assertThat(pool.isClosed()).isTrue();
    }
}
