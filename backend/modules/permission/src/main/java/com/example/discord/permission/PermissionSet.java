package com.example.discord.permission;

public record PermissionSet(long raw) {
    public static PermissionSet empty() {
        return new PermissionSet(0);
    }

    public PermissionSet grant(Permission permission) {
        return new PermissionSet(raw | permission.bit());
    }

    public PermissionSet revoke(Permission permission) {
        return new PermissionSet(raw & ~permission.bit());
    }

    public PermissionSet grantAll(PermissionSet permissions) {
        return new PermissionSet(raw | permissions.raw);
    }

    public PermissionSet revokeAll(PermissionSet permissions) {
        return new PermissionSet(raw & ~permissions.raw);
    }

    public boolean allows(Permission permission) {
        return (raw & Permission.ADMINISTRATOR.bit()) != 0 || (raw & permission.bit()) != 0;
    }
}
