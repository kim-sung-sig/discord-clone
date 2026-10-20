package com.example.discord.identityservice;

import java.util.Base64;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "discord.auth.mfa.enabled", havingValue = "true")
class MfaConfiguration {
    @Bean
    DataSource mfaDataSource(Environment environment) {
        String prefix = "discord.auth.mfa.";
        return new DriverManagerDataSource(
            environment.getRequiredProperty(prefix + "jdbc-url"),
            environment.getRequiredProperty(prefix + "database-user"),
            environment.getRequiredProperty(prefix + "database-password"));
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
