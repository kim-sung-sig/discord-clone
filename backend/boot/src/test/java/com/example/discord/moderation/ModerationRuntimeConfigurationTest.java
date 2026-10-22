package com.example.discord.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ModerationRuntimeConfigurationTest {
    @Test
    void runtimeConfigurationMustNotRegisterProcessLocalModeration() {
        new ApplicationContextRunner().withUserConfiguration(ModerationConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(InMemoryModerationService.class);
            });
    }

    @Test
    void testProfileUsesInMemoryAdapter() {
        new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=test")
            .withUserConfiguration(ModerationConfiguration.class, TestModerationConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(ModerationService.class))
                    .isExactlyInstanceOf(InMemoryModerationService.class);
            });
    }

    @Test
    void postgresSelectsJdbcWithoutStartupLoading() {
        DataSource database = mock(DataSource.class);
        new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=postgres")
            .withUserConfiguration(ModerationConfiguration.class)
            .withBean("postgresFlyway", Object.class, Object::new)
            .withBean(DataSource.class, () -> database)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(ModerationService.class)).isExactlyInstanceOf(JdbcModerationService.class);
                assertThat(context).doesNotHaveBean(InMemoryModerationService.class);
                verifyNoInteractions(database);
            });
    }
}
