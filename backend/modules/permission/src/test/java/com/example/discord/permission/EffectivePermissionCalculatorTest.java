package com.example.discord.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EffectivePermissionCalculatorTest {
    private final EffectivePermissionCalculator calculator = new EffectivePermissionCalculator();

    @Test
    void channelDenyOverridesRoleAllow() {
        UUID roleId = UUID.randomUUID();

        PermissionSet effective = calculator.calculate(
            PermissionSet.empty(),
            List.of(new RolePermission(roleId, PermissionSet.empty().grant(Permission.VIEW_CHANNEL))),
            List.of(new PermissionOverwrite(
                roleId,
                PermissionSet.empty(),
                PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
            ))
        );

        assertThat(effective.allows(Permission.VIEW_CHANNEL)).isFalse();
    }

    @Test
    void channelAllowCanGrantPermissionMissingFromRoles() {
        UUID roleId = UUID.randomUUID();

        PermissionSet effective = calculator.calculate(
            PermissionSet.empty(),
            List.of(new RolePermission(roleId, PermissionSet.empty())),
            List.of(new PermissionOverwrite(
                roleId,
                PermissionSet.empty().grant(Permission.VIEW_CHANNEL),
                PermissionSet.empty()
            ))
        );

        assertThat(effective.allows(Permission.VIEW_CHANNEL)).isTrue();
    }

    @Test
    void administratorBypassesChannelDeny() {
        UUID adminRoleId = UUID.randomUUID();

        PermissionSet effective = calculator.calculate(
            PermissionSet.empty(),
            List.of(new RolePermission(adminRoleId, PermissionSet.empty().grant(Permission.ADMINISTRATOR))),
            List.of(new PermissionOverwrite(
                adminRoleId,
                PermissionSet.empty(),
                PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
            ))
        );

        assertThat(effective.allows(Permission.VIEW_CHANNEL)).isTrue();
        assertThat(effective.allows(Permission.MANAGE_CHANNELS)).isTrue();
    }

    @Test
    void roleDenyOverridesEveryoneAllowForSameChannel() {
        UUID everyoneRoleId = UUID.randomUUID();
        UUID restrictedRoleId = UUID.randomUUID();

        PermissionSet effective = calculator.calculate(
            PermissionSet.empty(),
            List.of(new RolePermission(restrictedRoleId, PermissionSet.empty())),
            List.of(
                new PermissionOverwrite(
                    everyoneRoleId,
                    PermissionSet.empty().grant(Permission.VIEW_CHANNEL),
                    PermissionSet.empty()
                ),
                new PermissionOverwrite(
                    restrictedRoleId,
                    PermissionSet.empty(),
                    PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
                )
            ),
            everyoneRoleId
        );

        assertThat(effective.allows(Permission.VIEW_CHANNEL)).isFalse();
    }
}
