package com.example.discord.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("admin-cli")
final class GlobalAdminRoleCommandRunner implements ApplicationRunner {
    private final AuthStore store;
    private final Clock clock;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    GlobalAdminRoleCommandRunner(AuthStore store) {
        this(store, Clock.systemUTC(), null, null);
    }

    GlobalAdminRoleCommandRunner(AuthStore store, Clock clock) {
        this(store, clock, null, null);
    }

    @Autowired
    GlobalAdminRoleCommandRunner(
        AuthStore store,
        Environment environment,
        ConfigurableApplicationContext context
    ) {
        this(store, Clock.systemUTC(), environment, context);
    }

    private GlobalAdminRoleCommandRunner(
        AuthStore store,
        Clock clock,
        Environment environment,
        ConfigurableApplicationContext context
    ) {
        this.store = store;
        this.clock = clock;
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            System.out.println(runCommand(environment));
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    String runCommand(Environment source) {
        if (source == null) {
            throw new IllegalArgumentException("admin role command environment is required");
        }
        String command = required(source, "discord.admin-role.command").toLowerCase(java.util.Locale.ROOT);
        UUID userId = UUID.fromString(required(source, "discord.admin-role.user-id"));
        String role = GlobalRole.canonical(source.getProperty("discord.admin-role.role", GlobalRole.SECURITY_ADMIN));
        requireUser(userId);

        return switch (command) {
            case "grant" -> grant(source, userId, role);
            case "revoke" -> revoke(source, userId, role);
            case "list" -> list(userId);
            default -> throw new IllegalArgumentException("unsupported admin role command: " + command);
        };
    }

    private String grant(Environment source, UUID userId, String role) {
        requireConfirmation(source);
        boolean granted = store.grantGlobalRole(userId, role);
        recordAudit(
            source,
            userId,
            role,
            GlobalRoleAuditAction.GRANT,
            granted ? GlobalRoleAuditResult.APPLIED : GlobalRoleAuditResult.NOOP
        );
        return granted
            ? "granted %s to %s".formatted(role, userId)
            : "%s was already present for %s".formatted(role, userId);
    }

    private String revoke(Environment source, UUID userId, String role) {
        requireConfirmation(source);
        boolean revoked = store.revokeGlobalRole(userId, role);
        recordAudit(
            source,
            userId,
            role,
            GlobalRoleAuditAction.REVOKE,
            revoked ? GlobalRoleAuditResult.APPLIED : GlobalRoleAuditResult.NOOP
        );
        return revoked
            ? "revoked %s from %s".formatted(role, userId)
            : "%s was not present for %s".formatted(role, userId);
    }

    private String list(UUID userId) {
        return "global roles for %s: %s".formatted(userId, String.join(",", store.globalRolesForUser(userId)));
    }

    private void requireUser(UUID userId) {
        if (store.findById(userId).isEmpty()) {
            throw new IllegalArgumentException("user not found: " + userId);
        }
    }

    private void recordAudit(
        Environment source,
        UUID userId,
        String role,
        GlobalRoleAuditAction action,
        GlobalRoleAuditResult result
    ) {
        store.recordGlobalRoleAudit(new GlobalRoleAuditEntry(
            userId,
            role,
            action,
            source.getProperty("discord.admin-role.actor", "admin-cli"),
            result,
            Instant.now(clock)
        ));
    }

    private static void requireConfirmation(Environment source) {
        if (!"true".equals(source.getProperty("discord.admin-role.confirm"))) {
            throw new IllegalArgumentException("mutating admin role commands require discord.admin-role.confirm=true");
        }
    }

    private static String required(Environment source, String key) {
        String value = source.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }
}
