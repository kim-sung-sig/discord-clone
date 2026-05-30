# T122 Admin Role Runbook Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T122 Admin Role Runbook

## Document

Path: `docs/runbooks/global-admin-role-runbook.md`

## Sections

| Section | Purpose |
| --- | --- |
| Purpose | Defines when to use the runbook and what `SECURITY_ADMIN` controls. |
| Pre-check | Captures ticket, approver, target user, database readiness, and smoke check. |
| Resolve Target User ID | Shows a read-only lookup pattern and verification fields. |
| Environment | Keeps Postgres credentials in environment variables. |
| Grant | Runs `discord.admin-role.command=grant` with explicit confirmation. |
| Verify | Uses CLI list, `/api/users/@me`, and audit API review. |
| Rollback | Runs `discord.admin-role.command=revoke` and verifies audit evidence. |
| Failure Handling | Stops on wrong user, missing confirmation, DB failures, or missing audit evidence. |

## Security Review

- Database credentials are never placed in Gradle `--args`.
- Mutating commands require `discord.admin-role.confirm=true`.
- Audit review uses an already-authorized security admin bearer token.
- The runbook explicitly stops on missing audit evidence instead of repeating privileged changes.
