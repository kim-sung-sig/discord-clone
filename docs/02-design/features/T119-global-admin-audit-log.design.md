# T119 Global Admin Audit Log Design

Date: 2026-05-19
Slice: T119 Global Admin Audit Log

## Architecture

Global admin role auditing lives in the auth boundary because the auth store owns users, global roles, and admin CLI role mutations. The CLI records audit events after confirmed mutating commands, while storage implementations provide append and query methods.

## Components

- `GlobalRoleAuditEntry`
  - Target user, canonical role, action, actor, result, and occurrence time.
  - Actor is trimmed, bounded to 128 characters, and defaults to `admin-cli`.

- `GlobalRoleAuditAction`
  - `GRANT`
  - `REVOKE`

- `GlobalRoleAuditResult`
  - `APPLIED`
  - `NOOP`

- `AuthStore`
  - Adds `recordGlobalRoleAudit`.
  - Adds `globalRoleAuditLog`.

- `GlobalAdminRoleCommandRunner`
  - Records grant and revoke results.
  - Uses an injectable clock in tests.
  - Accepts optional `discord.admin-role.actor`.

## Data Flow

1. Operator runs an admin CLI command.
2. Runner validates command, confirmation, target user, and role.
3. Runner applies grant or revoke.
4. Runner records audit entry with the command result.
5. Store persists or retains audit entry depending on active profile.

## Storage

Postgres uses `user_global_role_audit_log` with target user, role, action, actor, result, and timestamp columns. The audit table references `users(id)` and is indexed by target user and timestamp for operator review queries.

## Testing

The CLI test verifies audit creation with deterministic time and actor. The Postgres store test verifies round-trip persistence and empty results for unrelated users.
