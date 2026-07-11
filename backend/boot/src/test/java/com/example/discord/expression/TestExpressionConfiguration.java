package com.example.discord.expression;

import static org.mockito.Mockito.mock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("test")
class TestExpressionConfiguration {
    @Bean
    ExpressionService expressionService() {
        return mock(ExpressionService.class);
    }
}
