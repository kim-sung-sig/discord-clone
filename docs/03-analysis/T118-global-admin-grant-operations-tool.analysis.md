# T118 Global Admin Grant Operations Tool Analysis

## Result

Backend global admin roles now have an operational CLI workflow.

## Behavior

- `admin-cli` profile runs Spring Boot with `web-application-type: none`.
- `grant`, `revoke`, and `list` commands operate on backend `AuthStore`.
- Mutating commands require `discord.admin-role.confirm=true`.
- Commands reject unknown users before mutating roles.
- Roles are canonicalized through the T111 `GlobalRole` contract.

## Example Commands

Grant:

```powershell
.\gradlew.bat :backend:boot:bootRun --args="--spring.profiles.active=postgres,admin-cli --discord.admin-role.command=grant --discord.admin-role.user-id=<uuid> --discord.admin-role.role=SECURITY_ADMIN --discord.admin-role.confirm=true"
```

Revoke:

```powershell
.\gradlew.bat :backend:boot:bootRun --args="--spring.profiles.active=postgres,admin-cli --discord.admin-role.command=revoke --discord.admin-role.user-id=<uuid> --discord.admin-role.role=SECURITY_ADMIN --discord.admin-role.confirm=true"
```

List:

```powershell
.\gradlew.bat :backend:boot:bootRun --args="--spring.profiles.active=postgres,admin-cli --discord.admin-role.command=list --discord.admin-role.user-id=<uuid>"
```

## Residual Risk

- Grant/revoke operations are not yet audited in a dedicated table.
- The runner prints to stdout and closes the Spring context, but there is not yet an integration test that runs the actual Gradle `bootRun` command.

