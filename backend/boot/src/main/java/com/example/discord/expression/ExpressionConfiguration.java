package com.example.discord.expression;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class ExpressionConfiguration {
    @Bean
    @Profile("postgres")
    ExpressionService expressionService(DataSource dataSource) {
        return new JdbcExpressionService(dataSource);
    }
}
