package com.example.discord.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class PostgresDataSourceTest {
    @Test
    void postgresDataSourceUsesConnectionPool() {
        var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://localhost/example")
            .withProperty("spring.datasource.username", "test-user")
            .withProperty("spring.datasource.password", "test-password");
        var dataSource = new PostgresPersistenceConfiguration().postgresDataSource(env);
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
        assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost/example");
        assertThat(dataSource.getUsername()).isEqualTo("test-user");
        assertThat(dataSource.getPassword()).isEqualTo("test-password");
        assertThat(dataSource.getPoolName()).isEqualTo("discord-boot");
        dataSource.close();
        env.setProperty("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", "3");
        try (var overridden = new PostgresPersistenceConfiguration().postgresDataSource(env)) {
            assertThat(overridden.getMaximumPoolSize()).isEqualTo(3);
        }
        env.setProperty("spring.datasource.hikari.maximum-pool-size", "4");
        var context = new AnnotationConfigApplicationContext();
        context.registerBean("pool", com.zaxxer.hikari.HikariDataSource.class,
            () -> new PostgresPersistenceConfiguration().postgresDataSource(env));
        context.refresh();
        var pool = context.getBean(com.zaxxer.hikari.HikariDataSource.class);
        assertThat(pool.getMaximumPoolSize()).isEqualTo(4);
        context.close();
        assertThat(pool.isClosed()).isTrue();
    }
}
