package com.example.discord.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthzProjectionUpdatedTest {
    @Test
    void acceptsValidProjectionEvent() {
        UUID guildId = UUID.randomUUID();
        AuthzProjectionUpdated event = new AuthzProjectionUpdated(
            UUID.randomUUID(),
            guildId,
            UUID.randomUUID(),
            AuthorizationResourceType.CHANNEL,
            UUID.randomUUID(),
            8L,
            3L,
            AuthorizationAudience.MESSAGE,
            Instant.parse("2026-08-09T00:00:00Z"),
            UUID.randomUUID()
        );

        assertThat(event.guildId()).isEqualTo(guildId);
        assertThat(event.permissionVersion()).isEqualTo(3L);
    }

    @Test
    void rejectsNegativePermissionVersion() {
        assertThatThrownBy(() -> new AuthzProjectionUpdated(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            AuthorizationResourceType.GUILD, UUID.randomUUID(), 0L, -1L,
            AuthorizationAudience.MESSAGE, Instant.now(), UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("permissionVersion must not be negative");
    }

    @Test
    void rejectsNullContractFields() {
        assertThatThrownBy(() -> new AuthzProjectionUpdated(
            null, UUID.randomUUID(), UUID.randomUUID(), AuthorizationResourceType.GUILD,
            UUID.randomUUID(), 0L, 0L, AuthorizationAudience.MESSAGE, Instant.now(), UUID.randomUUID()))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("eventId must not be null");
    }
}
