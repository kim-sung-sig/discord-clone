package com.example.discord.identityservice;

import java.util.Base64;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import com.zaxxer.hikari.HikariDataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "discord.auth.mfa.enabled", havingValue = "true")
class MfaConfiguration {
    @Bean
    HikariDataSource mfaDataSource(Environment environment) {
        String prefix = "discord.auth.mfa.";
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(environment.getRequiredProperty(prefix + "jdbc-url"));
        dataSource.setUsername(environment.getRequiredProperty(prefix + "database-user"));
        dataSource.setPassword(environment.getRequiredProperty(prefix + "database-password"));
        dataSource.setMaximumPoolSize(environment.getProperty(prefix + "maximum-pool-size", Integer.class,
            environment.getProperty("DISCORD_AUTH_MFA_MAXIMUM_POOL_SIZE", Integer.class, 10)));
        dataSource.setPoolName("discord-identity-mfa");
        return dataSource;
    }

    @Bean
    JdbcTotpStore jdbcTotpStore(Environment environment, DataSource mfaDataSource) {
        String prefix = "discord.auth.mfa.";
        byte[] key = Base64.getDecoder().decode(environment.getRequiredProperty(prefix + "encryption-key"));
        try {
            var cipher = new SecretCipher(key);
            return new JdbcTotpStore(mfaDataSource, cipher);
        } finally {
            java.util.Arrays.fill(key, (byte) 0);
        }
    }
}
