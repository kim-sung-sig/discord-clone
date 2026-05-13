package com.example.discord.expression;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ExpressionConfiguration {
    @Bean
    InMemoryExpressionService expressionService() {
        return new InMemoryExpressionService();
    }
}
