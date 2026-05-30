package com.example.discord.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("postgres")
class PostgresPersistenceConfiguration {
    @Bean
    DataSource postgresDataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password
    ) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean(initMethod = "migrate")
    Flyway postgresFlyway(
        DataSource postgresDataSource,
        @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
        @Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate
    ) {
        return Flyway.configure()
            .dataSource(postgresDataSource)
            .locations(locations)
            .baselineOnMigrate(baselineOnMigrate)
            .load();
    }
}
