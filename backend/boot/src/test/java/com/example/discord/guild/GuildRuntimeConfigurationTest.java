package com.example.discord.guild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GuildRuntimeConfigurationTest {
    @Test
    void postgresWiringUsesDatabaseStoreWithoutLoadingStateDuringStartup() {
        DataSource dataSource = mock(DataSource.class);
        new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=postgres")
            .withUserConfiguration(GuildConfiguration.class, JdbcGuildStore.class)
            .withBean("postgresFlyway", Object.class, Object::new)
            .withBean(DataSource.class, () -> dataSource)
            .withBean(GuildMutationAuthenticationInterceptor.class, () -> mock(GuildMutationAuthenticationInterceptor.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(GuildStore.class).hasSingleBean(GuildService.class);
                assertThat(context.getBean(GuildService.class)).isExactlyInstanceOf(GuildService.class);
                assertThat(context.getBean(GuildStore.class)).isInstanceOf(JdbcGuildStore.class);
                verifyNoInteractions(dataSource);
            });
    }

    @Test
    void testAndPostgresProfilesSelectOnlyDatabaseGuildService() {
        new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=test,postgres")
            .withUserConfiguration(GuildConfiguration.class, JdbcGuildStore.class)
            .withBean("postgresFlyway", Object.class, Object::new)
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(GuildMutationAuthenticationInterceptor.class, () -> mock(GuildMutationAuthenticationInterceptor.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(GuildService.class).doesNotHaveBean(InMemoryGuildService.class);
                assertThat(context.getBean(GuildService.class)).isExactlyInstanceOf(GuildService.class);
            });
    }

    @Test
    void missingBackingProfileCannotRegisterMemoryGuildService() {
        new ApplicationContextRunner()
            .withUserConfiguration(GuildConfiguration.class)
            .withBean(GuildMutationAuthenticationInterceptor.class, () -> mock(GuildMutationAuthenticationInterceptor.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(GuildService.class);
            });
    }

    @Test
    void testProfileUsesInMemoryAdapter() {
        new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=test")
            .withUserConfiguration(GuildConfiguration.class)
            .withBean(GuildMutationAuthenticationInterceptor.class, () -> mock(GuildMutationAuthenticationInterceptor.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(GuildService.class);
                assertThat(context.getBean(GuildService.class)).isExactlyInstanceOf(InMemoryGuildService.class);
            });
    }
}
