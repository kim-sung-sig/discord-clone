# Global Admin Role Runbook

Date: 2026-05-20
Scope: Grant, verify, audit, and revoke backend-owned global admin roles.

## Purpose

Use this runbook when an operator must grant or revoke `SECURITY_ADMIN` for an existing user. `SECURITY_ADMIN` is stored in backend persistence and is the authority source for admin-only security operations.

## Pre-check

- Confirm the change request, ticket, approving owner, target user, and intended duration.
- Confirm the target user already exists. The admin CLI refuses unknown user IDs.
- Confirm central Postgres is reachable and migrations are current.
- Run the smoke before a production change window when possible:

```powershell
powershell -ExecutionPolicy Bypass -File qa/admin-cli-bootrun-smoke.ps1
```

By default, the smoke creates a unique temporary user fixture, runs list/grant/list/revoke/list through the real `bootRun` path, verifies role and audit persistence, and removes its user, auth, role, session, and audit rows before exiting. If you pass `-SmokeUserId`, treat that ID as caller-owned: the script keeps compatibility with custom fixtures and does not auto-delete that user.

## Resolve Target User ID

Prefer an internal support/admin lookup flow. If direct database lookup is the only available path, use a read-only query and verify both account and profile fields:

```sql
SELECT u.id, u.username, u.display_name, a.email
FROM auth_accounts a
JOIN users u ON u.id = a.user_id
WHERE lower(a.email) = lower('<target-email>');
```

Record the selected user ID in the ticket before running a mutating command.

## Environment

Set database connection values as environment variables in the shell or CI secret context.

PowerShell:

```powershell
$env:POSTGRES_JDBC_URL = 'jdbc:postgresql://<host>:5432/<database>'
$env:POSTGRES_USER = '<user>'
$env:POSTGRES_PASSWORD = '<password>'
```

Bash:

```bash
export POSTGRES_JDBC_URL='jdbc:postgresql://<host>:5432/<database>'
export POSTGRES_USER='<user>'
export POSTGRES_PASSWORD='<password>'
```

Do not pass database passwords in Gradle --args. Command-line arguments are easier to expose through process listings and logs.

## Grant SECURITY_ADMIN

PowerShell:

```powershell
.\gradlew.bat :backend:boot:bootRun --args="--spring.profiles.active=admin-cli,postgres --spring.main.web-application-type=none --discord.admin-role.command=grant --discord.admin-role.user-id=<target-user-id> --discord.admin-role.role=SECURITY_ADMIN --discord.admin-role.actor=<ticket-or-operator> --discord.admin-role.confirm=true"
```

Bash:

```bash
./gradlew :backend:boot:bootRun --args='--spring.profiles.active=admin-cli,postgres --spring.main.web-application-type=none --discord.admin-role.command=grant --discord.admin-role.user-id=<target-user-id> --discord.admin-role.role=SECURITY_ADMIN --discord.admin-role.actor=<ticket-or-operator> --discord.admin-role.confirm=true'
```

Expected output includes:

```text
granted SECURITY_ADMIN to <target-user-id>
```

If the target already has the role, expected output includes:

```text
SECURITY_ADMIN was already present for <target-user-id>
```

That duplicate-safe path must still record an audit entry with `action: GRANT` and `result: NOOP`.

## Verify

List the backend role state:

```powershell
.\gradlew.bat :backend:boot:bootRun --args="--spring.profiles.active=admin-cli,postgres --spring.main.web-application-type=none --discord.admin-role.command=list --discord.admin-role.user-id=<target-user-id> --discord.admin-role.role=SECURITY_ADMIN"
```

Expected output includes:

```text
global roles for <target-user-id>: SECURITY_ADMIN
```

Verify the authenticated user contract after the target signs in or refreshes their access token:

```bash
curl -fsS -H "Authorization: Bearer <target-access-token>" \
  "<api-base-url>/api/users/@me"
```

Expected response includes `admin: true` and `roles` containing `SECURITY_ADMIN`.

Review audit evidence with a separate already-authorized security admin token:

```bash
curl -fsS -H "Authorization: Bearer <security-admin-access-token>" \
  "<api-base-url>/api/admin/global-roles/audit-log?targetUserId=<target-user-id>&limit=20"
```

Expected audit entry includes `role: SECURITY_ADMIN`, `action: GRANT`, `actor: <ticket-or-operator>`, and `result: APPLIED`. If the grant was a duplicate, the expected result is `NOOP`.

The audit response also includes the active retention and export policy. Expected policy values:

- `retention.maxAgeDays`: `365`
- `export.formats`: `json`
- `export.maxEntriesPerRequest`: `100`
- `export.requiresRole`: `SECURITY_ADMIN`

Treat the JSON response as the supported export format. Store exports only in the approved ticket/evidence system and avoid forwarding them to chat channels or personal storage. If entries older than the retention window are required for an investigation, use database backup/PITR evidence rather than bypassing the API policy.

## Rollback

Revoke the role as soon as the approved access window ends or if the grant was made for the wrong user.

PowerShell:

```powershell
.\gradlew.bat :backend:boot:bootRun --args="--spring.profiles.active=admin-cli,postgres --spring.main.web-application-type=none --discord.admin-role.command=revoke --discord.admin-role.user-id=<target-user-id> --discord.admin-role.role=SECURITY_ADMIN --discord.admin-role.actor=<ticket-or-operator> --discord.admin-role.confirm=true"
```

Bash:

```bash
./gradlew :backend:boot:bootRun --args='--spring.profiles.active=admin-cli,postgres --spring.main.web-application-type=none --discord.admin-role.command=revoke --discord.admin-role.user-id=<target-user-id> --discord.admin-role.role=SECURITY_ADMIN --discord.admin-role.actor=<ticket-or-operator> --discord.admin-role.confirm=true'
```

Then rerun the list command and audit-log review. Expected revoke audit entry includes `action: REVOKE`. If the role was already absent, the CLI records `result: NOOP`.

## Failure Handling

- `user not found`: stop and re-check the target user ID. Do not create a user from this runbook.
- `mutating admin role commands require discord.admin-role.confirm=true`: rerun only after confirming the ticket and target user.
- Database connection failure: stop and verify `POSTGRES_JDBC_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, network path, and current runtime profile.
- Missing audit entry: do not continue granting additional users. Preserve CLI logs and investigate persistence before repeating the command.
