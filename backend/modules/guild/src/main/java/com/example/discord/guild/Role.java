package com.example.discord.guild;

import com.example.discord.permission.PermissionSet;
import java.util.Objects;
import java.util.UUID;

public record Role(UUID id, String name, PermissionSet permissions) {
    public Role {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
    }

    Role withPermissions(PermissionSet updatedPermissions) {
        return new Role(id, name, updatedPermissions);
    }
}
