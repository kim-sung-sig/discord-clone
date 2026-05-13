package com.example.discord.permission;

import java.util.Objects;
import java.util.UUID;

public record PermissionOverwrite(UUID roleId, PermissionSet allow, PermissionSet deny) {
    public PermissionOverwrite {
        Objects.requireNonNull(roleId, "roleId must not be null");
        Objects.requireNonNull(allow, "allow must not be null");
        Objects.requireNonNull(deny, "deny must not be null");
    }
}
