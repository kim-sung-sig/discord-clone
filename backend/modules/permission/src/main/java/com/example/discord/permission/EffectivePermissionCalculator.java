package com.example.discord.permission;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

public final class EffectivePermissionCalculator {
    public PermissionSet calculate(
        PermissionSet everyonePermissions,
        Collection<RolePermission> rolePermissions,
        Collection<PermissionOverwrite> overwrites
    ) {
        return calculate(everyonePermissions, rolePermissions, overwrites, null);
    }

    public PermissionSet calculate(
        PermissionSet everyonePermissions,
        Collection<RolePermission> rolePermissions,
        Collection<PermissionOverwrite> overwrites,
        UUID everyoneRoleId
    ) {
        Objects.requireNonNull(everyonePermissions, "everyonePermissions must not be null");
        Objects.requireNonNull(rolePermissions, "rolePermissions must not be null");
        Objects.requireNonNull(overwrites, "overwrites must not be null");

        PermissionSet effective = everyonePermissions;
        for (RolePermission rolePermission : rolePermissions) {
            effective = effective.grantAll(rolePermission.permissions());
        }

        if (effective.allows(Permission.ADMINISTRATOR)) {
            return effective;
        }

        if (everyoneRoleId != null) {
            for (PermissionOverwrite overwrite : overwrites) {
                if (everyoneRoleId.equals(overwrite.roleId())) {
                    effective = effective.revokeAll(overwrite.deny()).grantAll(overwrite.allow());
                }
            }
        }

        for (PermissionOverwrite overwrite : overwrites) {
            if (everyoneRoleId != null && everyoneRoleId.equals(overwrite.roleId())) {
                continue;
            }
            effective = effective.revokeAll(overwrite.deny());
        }
        for (PermissionOverwrite overwrite : overwrites) {
            if (everyoneRoleId != null && everyoneRoleId.equals(overwrite.roleId())) {
                continue;
            }
            effective = effective.grantAll(overwrite.allow());
        }

        return effective;
    }
}
