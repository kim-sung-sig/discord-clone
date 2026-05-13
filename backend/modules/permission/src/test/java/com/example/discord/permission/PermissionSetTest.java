package com.example.discord.permission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PermissionSetTest {
    @Test
    void grantsAndRevokesIndividualPermissionsUsingBitsets() {
        PermissionSet permissions = PermissionSet.empty()
            .grant(Permission.VIEW_CHANNEL)
            .grant(Permission.SEND_MESSAGES)
            .revoke(Permission.SEND_MESSAGES);

        assertThat(permissions.allows(Permission.VIEW_CHANNEL)).isTrue();
        assertThat(permissions.allows(Permission.SEND_MESSAGES)).isFalse();
        assertThat(permissions.raw()).isEqualTo(Permission.VIEW_CHANNEL.bit());
    }

    @Test
    void administratorImpliesEveryPermission() {
        PermissionSet permissions = PermissionSet.empty().grant(Permission.ADMINISTRATOR);

        assertThat(permissions.allows(Permission.MANAGE_CHANNELS)).isTrue();
        assertThat(permissions.allows(Permission.CONNECT)).isTrue();
    }
}
