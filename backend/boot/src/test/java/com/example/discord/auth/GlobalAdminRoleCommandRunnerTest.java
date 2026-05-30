package com.example.discord.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.discord.identity.EmailAddress;
import com.example.discord.user.UserProfile;
import com.example.discord.user.Username;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.Constructor;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class GlobalAdminRoleCommandRunnerTest {
    private final InMemoryAuthStore store = new InMemoryAuthStore();
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(
            userId,
            Username.from("admin" + userId.toString().substring(0, 8)),
            "Admin User",
            Instant.parse("2026-05-19T00:00:00Z")
        );
        store.saveIfAbsent(new AuthAccount(
            EmailAddress.from("admin-" + userId + "@example.com"),
            "hashed-password",
            profile
        ));
    }

    @Test
    void grantsListsAndRevokesSecurityAdminWithExplicitConfirmation() {
        GlobalAdminRoleCommandRunner runner = new GlobalAdminRoleCommandRunner(store, Clock.fixed(
            Instant.parse("2026-05-19T01:00:00Z"),
            ZoneOffset.UTC
        ));

        assertThat(runner.runCommand(environment("grant", userId)
            .withProperty("discord.admin-role.confirm", "true")
            .withProperty("discord.admin-role.actor", "ops@example.com")))
            .contains("granted SECURITY_ADMIN");
        assertThat(store.globalRolesForUser(userId)).containsExactly("SECURITY_ADMIN");
        assertThat(store.globalRoleAuditLog(userId))
            .containsExactly(new GlobalRoleAuditEntry(
                userId,
                "SECURITY_ADMIN",
                GlobalRoleAuditAction.GRANT,
                "ops@example.com",
                GlobalRoleAuditResult.APPLIED,
                Instant.parse("2026-05-19T01:00:00Z")
            ));

        assertThat(runner.runCommand(environment("list", userId)))
            .contains("SECURITY_ADMIN");

        assertThat(runner.runCommand(environment("revoke", userId).withProperty("discord.admin-role.confirm", "true")))
            .contains("revoked SECURITY_ADMIN");
        assertThat(store.globalRolesForUser(userId)).isEmpty();
        assertThat(store.globalRoleAuditLog(userId))
            .extracting(GlobalRoleAuditEntry::action)
            .containsExactly(GlobalRoleAuditAction.GRANT, GlobalRoleAuditAction.REVOKE);
    }

    @Test
    void recordsNoopAuditResultForDuplicateGrant() {
        GlobalAdminRoleCommandRunner runner = new GlobalAdminRoleCommandRunner(store, Clock.fixed(
            Instant.parse("2026-05-19T01:00:00Z"),
            ZoneOffset.UTC
        ));

        assertThat(runner.runCommand(environment("grant", userId)
            .withProperty("discord.admin-role.confirm", "true")))
            .contains("granted SECURITY_ADMIN");
        assertThat(runner.runCommand(environment("grant", userId)
            .withProperty("discord.admin-role.confirm", "true")))
            .contains("SECURITY_ADMIN was already present");

        assertThat(store.globalRolesForUser(userId)).containsExactly("SECURITY_ADMIN");
        assertThat(store.globalRoleAuditLog(userId))
            .extracting(GlobalRoleAuditEntry::result)
            .containsExactly(GlobalRoleAuditResult.APPLIED, GlobalRoleAuditResult.NOOP);
    }

    @Test
    void rejectsMutatingCommandsWithoutExplicitConfirmation() {
        GlobalAdminRoleCommandRunner runner = new GlobalAdminRoleCommandRunner(store);

        assertThatThrownBy(() -> runner.runCommand(environment("grant", userId)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("discord.admin-role.confirm=true");
        assertThat(store.globalRolesForUser(userId)).isEmpty();
    }

    @Test
    void rejectsUnknownUsersBeforeMutation() {
        GlobalAdminRoleCommandRunner runner = new GlobalAdminRoleCommandRunner(store);
        UUID unknownUserId = UUID.randomUUID();

        assertThatThrownBy(() -> runner.runCommand(environment("grant", unknownUserId).withProperty("discord.admin-role.confirm", "true")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("user not found");
        assertThat(store.globalRolesForUser(unknownUserId)).isEmpty();
    }

    @Test
    void marksSpringBootRunnerConstructorForInjection() throws NoSuchMethodException {
        Constructor<GlobalAdminRoleCommandRunner> constructor = GlobalAdminRoleCommandRunner.class.getDeclaredConstructor(
            AuthStore.class,
            Environment.class,
            ConfigurableApplicationContext.class
        );

        assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue();
    }

    private static MockEnvironment environment(String command, UUID userId) {
        return new MockEnvironment()
            .withProperty("discord.admin-role.command", command)
            .withProperty("discord.admin-role.user-id", userId.toString())
            .withProperty("discord.admin-role.role", "security_admin");
    }
}
