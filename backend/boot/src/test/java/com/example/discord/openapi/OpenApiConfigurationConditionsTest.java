package com.example.discord.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class OpenApiConfigurationConditionsTest {
    @Test
    void nonWebRunsSuchAsAdminCliSkipTheContractConfiguration() {
        new ApplicationContextRunner()
            .withUserConfiguration(OpenApiConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(OpenApiConfiguration.class);
            });
    }

    @Test
    void disabledApiDocsSkipTheContractConfiguration() {
        new WebApplicationContextRunner()
            .withPropertyValues("springdoc.api-docs.enabled=false")
            .withUserConfiguration(OpenApiConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(OpenApiConfiguration.class);
            });
    }
}
