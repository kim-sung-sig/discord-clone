# T118 Global Admin Grant Operations Tool Design

## Command Profile

Add `application-admin-cli.yml`:

```yaml
spring:
  main:
    web-application-type: none
```

Operators run the backend with profiles such as:

```powershell
.\gradlew.bat :backend:boot:bootRun --args="--spring.profiles.active=postgres,admin-cli --discord.admin-role.command=grant --discord.admin-role.user-id=<uuid> --discord.admin-role.role=SECURITY_ADMIN --discord.admin-role.confirm=true"
```

## Commands

- `grant`: grant a canonical global role.
- `revoke`: revoke a canonical global role.
- `list`: list current global roles.

## Safety

- `grant` and `revoke` require `discord.admin-role.confirm=true`.
- All commands require an existing user id.
- Roles are canonicalized through the existing T111 `GlobalRole.canonical`.
- The CLI does not expose secrets or tokens.

## Implementation Units

- `AuthStore.revokeGlobalRole(UUID, String)`
- `GlobalAdminRoleOperations`
- `GlobalAdminRoleCommandRunner`

