package com.example.discord.permission;

import java.util.Objects;
import java.util.UUID;

public record RolePermission(UUID roleId, PermissionSet permissions) {
    public RolePermission {
        Objects.requireNonNull(roleId, "roleId must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
    }
}
