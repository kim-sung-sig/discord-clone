package com.example.discord.invite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class InviteRuntimeConfigurationTest {
    @Autowired
    private InviteService inviteService;

    @Test
    void postgresProfileUsesJdbcInviteService() {
        assertThat(inviteService).isInstanceOf(JdbcInviteService.class);
    }
}
