package com.example.discord.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Profile("postgres")
class PostgresPersistenceConfiguration {
    @Bean
    HikariDataSource postgresDataSource(Environment environment) {
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(environment.getRequiredProperty("spring.datasource.url"));
        dataSource.setUsername(environment.getRequiredProperty("spring.datasource.username"));
        dataSource.setPassword(environment.getRequiredProperty("spring.datasource.password"));
        dataSource.setMaximumPoolSize(environment.getProperty(
            "spring.datasource.hikari.maximum-pool-size", Integer.class,
            environment.getProperty("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", Integer.class, 10)));
        dataSource.setPoolName("discord-boot");
        return dataSource;
    }

    @Bean
    JdbcTemplate postgresJdbcTemplate(DataSource postgresDataSource) {
        return new JdbcTemplate(postgresDataSource);
    }

    @Bean
    PlatformTransactionManager postgresTransactionManager(DataSource postgresDataSource) {
        return new DataSourceTransactionManager(postgresDataSource);
    }

    @Bean(initMethod = "migrate")
    Flyway postgresFlyway(
        DataSource postgresDataSource,
        @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
        @Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate
    ) {
        var configuration = Flyway.configure()
            .dataSource(postgresDataSource)
            .locations(locations)
            .baselineOnMigrate(baselineOnMigrate);
        configuration.getConfigurationExtension(PostgreSQLConfigurationExtension.class).setTransactionalLock(false);
        return configuration.load();
    }
}
